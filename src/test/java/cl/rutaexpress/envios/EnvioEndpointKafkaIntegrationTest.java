package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.KafkaConfig;
import cl.rutaexpress.envios.dto.ActualizarEstadoRequest;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnvioEndpointKafkaIntegrationTest {

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
        List<TopicPartition> particiones = List.of(
                new TopicPartition(KafkaConfig.TOPICO_SHIPMENTS_EVENTS, 0),
                new TopicPartition(KafkaConfig.TOPICO_SHIPMENTS_EVENTS, 1),
                new TopicPartition(KafkaConfig.TOPICO_SHIPMENTS_EVENTS, 2)
        );
        testConsumer.assign(particiones);
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    @DisplayName("Validacion contra Cluster Kafka Real: Envio a shipments.events con particionamiento por clave")
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
        assertFalse(recordsCreacion.isEmpty(), "Debe haberse publicado el evento de creacion en shipments.events");

        ConsumerRecord<String, String> recordCreacion = recordsCreacion.get(0);
        assertEquals(String.valueOf(envioId), recordCreacion.key(), "La clave en Kafka debe ser envioId.toString()");
        assertEquals(KafkaConfig.TOPICO_SHIPMENTS_EVENTS, recordCreacion.topic());
        assertTrue(recordCreacion.partition() >= 0 && recordCreacion.partition() < 3, "La particion debe estar en el rango [0, 2]");

        JsonNode jsonCreacion = objectMapper.readTree(recordCreacion.value());
        assertEquals(envioId.longValue(), jsonCreacion.get("envioId").asLong());
        assertEquals(codigoGenerado, jsonCreacion.get("codigoSeguimiento").asText());
        assertTrue(jsonCreacion.get("estadoAnterior").isNull(), "El estado anterior en creacion debe ser null");
        assertEquals("CREADO", jsonCreacion.get("estadoNuevo").asText());
        assertEquals("sistema@rutaexpress.cl", jsonCreacion.get("usuarioEmail").asText());
        assertEquals("SISTEMA", jsonCreacion.get("usuarioRol").asText());
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
        assertFalse(recordsAceptado.isEmpty(), "Debe haberse publicado el evento de ACEPTADO en shipments.events");

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
