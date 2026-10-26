package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.ActualizarEstadoRequest;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;

/**
 * Prueba de integración con broker Kafka real. Requiere Docker levantado y perfil -Pintegracion-externa.
 */
@Tag("integracion-externa")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnvioEndpointKafkaIntegrationTest {

    private static final String TOPICO_PRUEBA = "test.shipments.events." + UUID.randomUUID();

    @DynamicPropertySource
    static void propiedadesBrokerReal(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("kafka.topic.shipments-events", () -> TOPICO_PRUEBA);
    }

    @BeforeAll
    static void inicializarTopicoPrueba() {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"))) {
            admin.createTopics(List.of(new NewTopic(TOPICO_PRUEBA, 3, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CatalogoClient catalogoClient;

    private KafkaConsumer<String, String> testConsumer;

    @BeforeEach
    void setUpConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-envios-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        testConsumer = new KafkaConsumer<>(props);
        testConsumer.subscribe(List.of(TOPICO_PRUEBA));

        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().add("X-Servicio-Interno", "test-secreto-interno-envios-seguro-32caracteres");
            request.getHeaders().add("X-Usuario-Oid", "oid-admin-test");
            request.getHeaders().add("X-Usuario-Email", "admin@rutaexpress.cl");
            request.getHeaders().add("X-Usuario-Roles", "ADMIN");
            return execution.execute(request, body);
        }));
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    @DisplayName("Validacion contra Cluster Kafka Real: Envio a topico aislado con particionamiento por clave")
    void testPublicacionHaciaClusterKafkaReal() throws Exception {
        doNothing().when(catalogoClient).descontarCapacidad(1L);

        // 1. Crear envio via REST API con codigo unico
        String codigo = "RE-KAFKA-" + UUID.randomUUID().toString().substring(0, 8);
        CrearEnvioRequest crearReq = TestEnvioFactory.crearRequestValido("11223344-5", "99887766-4", new BigDecimal("7.80"));

        ResponseEntity<EnvioResponse> crearRes = restTemplate.postForEntity(
                "/api/envios",
                crearReq,
                EnvioResponse.class
        );
        assertEquals(HttpStatus.CREATED, crearRes.getStatusCode());
        assertNotNull(crearRes.getBody());
        Long envioId = crearRes.getBody().id();
        assertNotNull(envioId);
        String codigoGenerado = crearRes.getBody().codigoSeguimiento();
        assertNotNull(codigoGenerado);

        // 2. Consumir evento de creacion desde Kafka real filtrando por codigo unico
        List<ConsumerRecord<String, String>> recordsCreacion = esperarMensajesPorCodigo(String.valueOf(envioId), codigoGenerado, 1, Duration.ofSeconds(10));
        assertFalse(recordsCreacion.isEmpty(), "Debe haberse publicado el evento de creacion en el topico de prueba aislado");

        ConsumerRecord<String, String> recordCreacion = recordsCreacion.get(0);
        assertEquals(String.valueOf(envioId), recordCreacion.key(), "La clave en Kafka debe ser envioId.toString()");
        assertEquals(TOPICO_PRUEBA, recordCreacion.topic());
        assertTrue(recordCreacion.partition() >= 0 && recordCreacion.partition() < 3, "La particion debe estar en el rango [0, 2]");

        JsonNode jsonCreacion = objectMapper.readTree(recordCreacion.value());
        assertEquals(envioId.longValue(), jsonCreacion.get("envioId").asLong());
        assertEquals(codigoGenerado, jsonCreacion.get("codigoSeguimiento").asText());
        assertTrue(jsonCreacion.get("estadoAnterior").isNull(), "El estado anterior en creacion debe ser null");
        assertEquals("CREADO", jsonCreacion.get("estadoNuevo").asText());
        assertEquals("admin@rutaexpress.cl", jsonCreacion.get("usuarioEmail").asText());
        assertEquals("ADMIN", jsonCreacion.get("usuarioRol").asText());
        assertNotNull(jsonCreacion.get("eventoId").asText());
        assertNotNull(jsonCreacion.get("timestamp").asText());

        int particionEsperada = recordCreacion.partition();

        // 3. Avanzar estado a ACEPTADO
        ActualizarEstadoRequest aceptarReq = new ActualizarEstadoRequest(EstadoEnvio.ACEPTADO, 1L);
        ResponseEntity<EnvioResponse> aceptarRes = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                new HttpEntity<>(aceptarReq),
                EnvioResponse.class
        );
        assertEquals(HttpStatus.OK, aceptarRes.getStatusCode());

        // 4. Consumir el nuevo evento de transicion (ACEPTADO) desde Kafka real
        List<ConsumerRecord<String, String>> recordsAceptado = esperarMensajesPorCodigo(String.valueOf(envioId), codigoGenerado, 1, Duration.ofSeconds(10));
        assertFalse(recordsAceptado.isEmpty(), "Debe haberse publicado el evento de ACEPTADO en el topico de prueba aislado");

        ConsumerRecord<String, String> recordAceptado = recordsAceptado.get(0);
        assertEquals(String.valueOf(envioId), recordAceptado.key());
        // Garantia de particionamiento secuencial: la misma clave debe residir en la misma particion
        assertEquals(particionEsperada, recordAceptado.partition(),
                "Todos los eventos del mismo envioId deben dirigirse a la misma particion para orden estricto");

        JsonNode jsonAceptado = objectMapper.readTree(recordAceptado.value());
        assertEquals(envioId.longValue(), jsonAceptado.get("envioId").asLong());
        assertEquals("CREADO", jsonAceptado.get("estadoAnterior").asText());
        assertEquals("ACEPTADO", jsonAceptado.get("estadoNuevo").asText());
    }

    private List<ConsumerRecord<String, String>> esperarMensajesPorCodigo(
            String claveEsperada,
            String codigoEsperado,
            int cantidadMinima,
            Duration timeoutTotal) {

        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        long fin = System.currentTimeMillis() + timeoutTotal.toMillis();

        while (System.currentTimeMillis() < fin && encontrados.size() < cantidadMinima) {
            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(300));
            for (ConsumerRecord<String, String> record : records) {
                if (claveEsperada.equals(record.key()) && record.value() != null && record.value().contains(codigoEsperado)) {
                    encontrados.add(record);
                }
            }
        }
        return encontrados;
    }
}
