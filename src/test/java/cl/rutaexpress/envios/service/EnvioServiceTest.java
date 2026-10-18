package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.dto.NotificacionEmailPayload;
import cl.rutaexpress.envios.dto.TicketBodegaPayload;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.EnvioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvioServiceTest {

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @Captor
    private ArgumentCaptor<MensajeEnvelope<?>> mensajeCaptor;

    private EnvioService envioService;

    @BeforeEach
    void setUp() {
        envioService = new EnvioService(envioRepository, catalogoClient, publicadorRabbitMQ);
    }

    private Envio crearEnvioMock(Long id, String codigo, EstadoEnvio estado) {
        Envio envio = new Envio(
                codigo,
                "11111111-1",
                "22222222-2",
                "Av. Providencia 1234",
                "Av. Apoquindo 5678",
                new BigDecimal("2.50"),
                estado
        );
        try {
            var field = Envio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(envio, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return envio;
    }

    @Test
    @DisplayName("Transición a ACEPTADO: Descuenta capacidad en catálogo y publica en email y warehouse")
    void testTransicionAceptadoExito() {
        Envio envio = crearEnvioMock(10L, "RE-TEST-001", EstadoEnvio.CREADO);
        when(envioRepository.findById(10L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(10L, EstadoEnvio.ACEPTADO, 2L);

        assertNotNull(response);
        assertEquals(EstadoEnvio.ACEPTADO, response.estado());

        // 1. Verifica descuento atómico de capacidad
        verify(catalogoClient, times(1)).descontarCapacidad(2L);

        // 2. Verifica guardado en BD
        verify(envioRepository, times(1)).save(envio);

        // 3. Verifica publicación en RabbitMQ (2 mensajes: email y bodega)
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
    }

    @Test
    @DisplayName("Transición a ACEPTADO con capacidad agotada: lanza excepción y no persiste ni publica")
    void testTransicionAceptadoCapacidadAgotada() {
        Envio envio = crearEnvioMock(10L, "RE-TEST-002", EstadoEnvio.CREADO);
        when(envioRepository.findById(10L)).thenReturn(Optional.of(envio));
        doThrow(new CapacidadFlotaAgotadaException(1L)).when(catalogoClient).descontarCapacidad(1L);

        assertThrows(CapacidadFlotaAgotadaException.class, () ->
                envioService.actualizarEstado(10L, EstadoEnvio.ACEPTADO, 1L)
        );

        verify(catalogoClient, times(1)).descontarCapacidad(1L);
        verify(envioRepository, never()).save(any(Envio.class));
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());
    }

    @Test
    @DisplayName("Transición a EN_RUTA: Publica evento email sin invocar descuento de capacidad")
    void testTransicionEnRutaExito() {
        Envio envio = crearEnvioMock(20L, "RE-TEST-003", EstadoEnvio.EN_BODEGA);
        when(envioRepository.findById(20L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(20L, EstadoEnvio.EN_RUTA);

        assertNotNull(response);
        assertEquals(EstadoEnvio.EN_RUTA, response.estado());

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                argThat((MensajeEnvelope<?> m) -> "EMAIL_ENVIO_EN_RUTA".equals(m.tipo()))
        );
    }

    @Test
    @DisplayName("Transición a ENTREGADO: Publica evento email sin invocar descuento de capacidad")
    void testTransicionEntregadoExito() {
        Envio envio = crearEnvioMock(30L, "RE-TEST-004", EstadoEnvio.EN_RUTA);
        when(envioRepository.findById(30L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(30L, EstadoEnvio.ENTREGADO);

        assertNotNull(response);
        assertEquals(EstadoEnvio.ENTREGADO, response.estado());

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                argThat((MensajeEnvelope<?> m) -> "EMAIL_ENVIO_ENTREGADO".equals(m.tipo()))
        );
    }

    @Test
    @DisplayName("Transición inválida: CREADO -> EN_RUTA lanza TransicionEstadoInvalidaException")
    void testTransicionInvalida() {
        Envio envio = crearEnvioMock(40L, "RE-TEST-005", EstadoEnvio.CREADO);
        when(envioRepository.findById(40L)).thenReturn(Optional.of(envio));

        assertThrows(TransicionEstadoInvalidaException.class, () ->
                envioService.actualizarEstado(40L, EstadoEnvio.EN_RUTA)
        );

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(envioRepository, never()).save(any());
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());
    }
}
