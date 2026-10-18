package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.EnvioRepository;
import cl.rutaexpress.envios.service.EnvioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class EnvioIntegrationTest {

    @Autowired
    private EnvioService envioService;

    @Autowired
    private EnvioRepository envioRepository;

    @MockBean
    private CatalogoClient catalogoClient;

    @MockBean
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @BeforeEach
    void setUp() {
        envioRepository.deleteAll();
    }

    @Test
    @DisplayName("Ciclo completo E2E: Crear -> Aceptar (descuento cupo y mensajes RabbitMQ) -> En Ruta -> Entregado")
    void testCicloCompletoEnvio() {
        // 1. Crear envio
        CrearEnvioRequest crearReq = new CrearEnvioRequest(
                "RE-INTEG-001",
                "15555666-7",
                "18888999-0",
                "Av. Alameda 100",
                "Av. Vitacura 500",
                new BigDecimal("4.20")
        );
        EnvioResponse creado = envioService.crear(crearReq);
        assertNotNull(creado.id());
        assertEquals(EstadoEnvio.CREADO, creado.estado());

        // 2. Transicion a ACEPTADO
        doNothing().when(catalogoClient).descontarCapacidad(1L);

        EnvioResponse aceptado = envioService.actualizarEstado(creado.id(), EstadoEnvio.ACEPTADO, 1L);
        assertEquals(EstadoEnvio.ACEPTADO, aceptado.estado());

        verify(catalogoClient, times(1)).descontarCapacidad(1L);
        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                argThat((MensajeEnvelope<?> m) -> "EMAIL_ENVIO_ACEPTADO".equals(m.tipo()))
        );
        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_WAREHOUSE),
                argThat((MensajeEnvelope<?> m) -> "TICKET_BODEGA_GENERAR".equals(m.tipo()))
        );

        // 3. Transicion a EN_BODEGA
        EnvioResponse enBodega = envioService.actualizarEstado(creado.id(), EstadoEnvio.EN_BODEGA);
        assertEquals(EstadoEnvio.EN_BODEGA, enBodega.estado());

        // 4. Transicion a EN_RUTA
        EnvioResponse enRuta = envioService.actualizarEstado(creado.id(), EstadoEnvio.EN_RUTA);
        assertEquals(EstadoEnvio.EN_RUTA, enRuta.estado());

        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                argThat((MensajeEnvelope<?> m) -> "EMAIL_ENVIO_EN_RUTA".equals(m.tipo()))
        );

        // 5. Transicion a ENTREGADO
        EnvioResponse entregado = envioService.actualizarEstado(creado.id(), EstadoEnvio.ENTREGADO);
        assertEquals(EstadoEnvio.ENTREGADO, entregado.estado());

        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                argThat((MensajeEnvelope<?> m) -> "EMAIL_ENVIO_ENTREGADO".equals(m.tipo()))
        );
    }
}
