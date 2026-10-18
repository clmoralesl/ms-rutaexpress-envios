package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.ActualizarEstadoRequest;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnvioEndpointRabbitMQIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private CatalogoClient catalogoClient;

    @BeforeEach
    void purgeQueues() {
        // Limpiar mensajes residuales de colas reales antes de cada prueba
        try {
            while (rabbitTemplate.receive(RabbitMQConfig.QUEUE_EMAIL, 100) != null) {}
            while (rabbitTemplate.receive(RabbitMQConfig.QUEUE_WAREHOUSE, 100) != null) {}
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Endpoint Real: Crear envio, avanzar a ACEPTADO y validar recepcion real en RabbitMQ")
    void testFlujoCompletoConRabbitMQReal() {
        // 1. Crear envio via POST /api/envios
        CrearEnvioRequest crearReq = new CrearEnvioRequest(
                "RE-LIVE-001",
                "12345678-5",
                "98765432-1",
                "Santiago Centro 100",
                "Las Condes 200",
                new BigDecimal("3.50")
        );

        ResponseEntity<EnvioResponse> crearResp = restTemplate.postForEntity(
                "/api/envios",
                crearReq,
                EnvioResponse.class
        );

        assertEquals(HttpStatus.CREATED, crearResp.getStatusCode());
        assertNotNull(crearResp.getBody());
        Long envioId = crearResp.getBody().id();
        assertEquals(EstadoEnvio.CREADO, crearResp.getBody().estado());

        // 2. Configurar mock de catalogo para simular descuento exitoso
        doNothing().when(catalogoClient).descontarCapacidad(1L);

        // 3. Transicionar a ACEPTADO via PUT /api/envios/{id}/estado
        ActualizarEstadoRequest aceptarReq = new ActualizarEstadoRequest(EstadoEnvio.ACEPTADO, 1L);
        HttpEntity<ActualizarEstadoRequest> requestEntity = new HttpEntity<>(aceptarReq);

        ResponseEntity<EnvioResponse> aceptarResp = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                requestEntity,
                EnvioResponse.class
        );

        assertEquals(HttpStatus.OK, aceptarResp.getStatusCode());
        assertNotNull(aceptarResp.getBody());
        assertEquals(EstadoEnvio.ACEPTADO, aceptarResp.getBody().estado());

        // 4. Validar consumo REAL desde la cola RabbitMQ q.cmd.email
        Object mensajeEmailRaw = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_EMAIL, 5000);
        assertNotNull(mensajeEmailRaw, "El mensaje debe haber llegado a la cola real q.cmd.email de RabbitMQ");
        assertTrue(mensajeEmailRaw instanceof MensajeEnvelope<?> || mensajeEmailRaw instanceof Map<?, ?>);

        // 5. Validar consumo REAL desde la cola RabbitMQ q.cmd.warehouse
        Object mensajeBodegaRaw = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_WAREHOUSE, 5000);
        assertNotNull(mensajeBodegaRaw, "El ticket de picking debe haber llegado a la cola real q.cmd.warehouse de RabbitMQ");

        // 6. Transicionar a EN_BODEGA
        ActualizarEstadoRequest bodegaReq = new ActualizarEstadoRequest(EstadoEnvio.EN_BODEGA, null);
        restTemplate.exchange("/api/envios/" + envioId + "/estado", HttpMethod.PUT, new HttpEntity<>(bodegaReq), EnvioResponse.class);

        // 7. Transicionar a EN_RUTA y verificar correo real en RabbitMQ
        ActualizarEstadoRequest enRutaReq = new ActualizarEstadoRequest(EstadoEnvio.EN_RUTA, null);
        ResponseEntity<EnvioResponse> enRutaResp = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                new HttpEntity<>(enRutaReq),
                EnvioResponse.class
        );
        assertEquals(HttpStatus.OK, enRutaResp.getStatusCode());

        Object emailEnRuta = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_EMAIL, 5000);
        assertNotNull(emailEnRuta, "El mensaje de envio en ruta debe llegar a q.cmd.email");

        // 8. Transicionar a ENTREGADO y verificar correo real en RabbitMQ
        ActualizarEstadoRequest entregadoReq = new ActualizarEstadoRequest(EstadoEnvio.ENTREGADO, null);
        ResponseEntity<EnvioResponse> entregadoResp = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                new HttpEntity<>(entregadoReq),
                EnvioResponse.class
        );
        assertEquals(HttpStatus.OK, entregadoResp.getStatusCode());

        Object emailEntregado = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_EMAIL, 5000);
        assertNotNull(emailEntregado, "El mensaje de entrega debe llegar a q.cmd.email");
    }

    @Test
    @DisplayName("Endpoint Real: Respuesta 409 Conflict cuando catalogo rechaza por capacidad agotada")
    void testRechazoPorCapacidadAgotadaEndpoint() {
        // Crear envio
        CrearEnvioRequest crearReq = new CrearEnvioRequest(
                "RE-LIVE-002",
                "12345678-5",
                "98765432-1",
                "Origen Prueba",
                "Destino Prueba",
                new BigDecimal("1.50")
        );
        ResponseEntity<EnvioResponse> crearResp = restTemplate.postForEntity("/api/envios", crearReq, EnvioResponse.class);
        Long envioId = crearResp.getBody().id();

        // Configurar Catalogo para lanzar CapacidadFlotaAgotadaException
        doThrow(new CapacidadFlotaAgotadaException(3L)).when(catalogoClient).descontarCapacidad(3L);

        // Invocar PUT /api/envios/{id}/estado
        ActualizarEstadoRequest aceptarReq = new ActualizarEstadoRequest(EstadoEnvio.ACEPTADO, 3L);
        ResponseEntity<Map<String, String>> errorResp = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                new HttpEntity<>(aceptarReq),
                new ParameterizedTypeReference<Map<String, String>>() {}
        );

        assertEquals(HttpStatus.CONFLICT, errorResp.getStatusCode());
        assertNotNull(errorResp.getBody());
        assertEquals("Capacidad de flota agotada para el servicio ID 3", errorResp.getBody().get("mensaje"));

        // Verificar que no se emitio ningun mensaje en RabbitMQ
        Object email = rabbitTemplate.receive(RabbitMQConfig.QUEUE_EMAIL, 500);
        assertNull(email, "No debe haberse publicado ningun mensaje en email");
    }

    @Test
    @DisplayName("Endpoint Real: Respuesta 400 Bad Request en transicion invalida")
    void testTransicionInvalidaEndpoint() {
        CrearEnvioRequest crearReq = new CrearEnvioRequest(
                "RE-LIVE-003",
                "12345678-5",
                "98765432-1",
                "Origen Prueba",
                "Destino Prueba",
                new BigDecimal("2.00")
        );
        ResponseEntity<EnvioResponse> crearResp = restTemplate.postForEntity("/api/envios", crearReq, EnvioResponse.class);
        Long envioId = crearResp.getBody().id();

        // Intentar pasar directamente de CREADO a ENTREGADO
        ActualizarEstadoRequest reqInvalido = new ActualizarEstadoRequest(EstadoEnvio.ENTREGADO, null);
        ResponseEntity<Map<String, String>> errorResp = restTemplate.exchange(
                "/api/envios/" + envioId + "/estado",
                HttpMethod.PUT,
                new HttpEntity<>(reqInvalido),
                new ParameterizedTypeReference<Map<String, String>>() {}
        );

        assertEquals(HttpStatus.BAD_REQUEST, errorResp.getStatusCode());
        assertNotNull(errorResp.getBody());
        assertTrue(errorResp.getBody().containsKey("mensaje"));
    }
}
