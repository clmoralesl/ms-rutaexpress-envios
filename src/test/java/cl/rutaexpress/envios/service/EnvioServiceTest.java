package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.dto.NotificacionEmailPayload;
import cl.rutaexpress.envios.dto.TicketBodegaPayload;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
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

    @Mock
    private PublicadorKafkaEnvios publicadorKafkaEnvios;

    @Captor
    private ArgumentCaptor<MensajeEnvelope<?>> mensajeCaptor;

    @Captor
    private ArgumentCaptor<EventoEnvioKafka> eventoKafkaCaptor;

    private EnvioService envioService;

    @BeforeEach
    void setUp() {
        envioService = new EnvioService(envioRepository, catalogoClient, publicadorRabbitMQ, publicadorKafkaEnvios);
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
    @DisplayName("Crear envio: persiste con estado CREADO y publica evento en Kafka")
    void testCrearEnvioExito() {
        CrearEnvioRequest request = new CrearEnvioRequest(
                "RE-TEST-NEW-01",
                "11111111-1",
                "22222222-2",
                "Av. Providencia 1234",
                "Av. Apoquindo 5678",
                new BigDecimal("3.00")
        );

        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> {
            Envio guardado = invocation.getArgument(0);
            var field = Envio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(guardado, 99L);
            return guardado;
        });

        EnvioResponse response = envioService.crear(request, "admin@rutaexpress.cl", "ADMINISTRADOR");

        assertNotNull(response);
        assertEquals(99L, response.id());
        assertEquals(EstadoEnvio.CREADO, response.estado());

        verify(envioRepository, times(1)).save(any(Envio.class));
        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());

        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(99L, evento.envioId());
        assertEquals("RE-TEST-NEW-01", evento.codigoSeguimiento());
        assertNull(evento.estadoAnterior());
        assertEquals(EstadoEnvio.CREADO, evento.estadoNuevo());
        assertEquals("admin@rutaexpress.cl", evento.usuarioEmail());
        assertEquals("ADMINISTRADOR", evento.usuarioRol());
        assertEquals(new BigDecimal("3.00"), evento.detalles().get("pesoKg"));
    }

    @Test
    @DisplayName("Transicion a ACEPTADO: Descuenta capacidad en catalogo, publica en RabbitMQ y Kafka")
    void testTransicionAceptadoExito() {
        Envio envio = crearEnvioMock(10L, "RE-TEST-001", EstadoEnvio.CREADO);
        when(envioRepository.findById(10L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(10L, EstadoEnvio.ACEPTADO, 2L);

        assertNotNull(response);
        assertEquals(EstadoEnvio.ACEPTADO, response.estado());

        // 1. Verifica descuento atomico de capacidad
        verify(catalogoClient, times(1)).descontarCapacidad(2L);

        // 2. Verifica guardado en BD
        verify(envioRepository, times(1)).save(envio);

        // 3. Verifica publicacion en RabbitMQ (2 mensajes: email y bodega)
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

        // 4. Verifica publicacion de evento en Kafka
        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(10L, evento.envioId());
        assertEquals(EstadoEnvio.CREADO, evento.estadoAnterior());
        assertEquals(EstadoEnvio.ACEPTADO, evento.estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a ACEPTADO con capacidad agotada: lanza excepcion y no persiste ni publica")
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
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("Transicion a EN_RUTA: Publica evento email y evento Kafka sin invocar descuento de capacidad")
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

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(20L, evento.envioId());
        assertEquals(EstadoEnvio.EN_BODEGA, evento.estadoAnterior());
        assertEquals(EstadoEnvio.EN_RUTA, evento.estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a ENTREGADO: Publica evento email y evento Kafka sin invocar descuento de capacidad")
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

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(30L, evento.envioId());
        assertEquals(EstadoEnvio.EN_RUTA, evento.estadoAnterior());
        assertEquals(EstadoEnvio.ENTREGADO, evento.estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a CANCELADO: Publica evento Kafka sin invocar RabbitMQ")
    void testTransicionCanceladoExito() {
        Envio envio = crearEnvioMock(50L, "RE-TEST-006", EstadoEnvio.CREADO);
        when(envioRepository.findById(50L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(50L, EstadoEnvio.CANCELADO);

        assertNotNull(response);
        assertEquals(EstadoEnvio.CANCELADO, response.estado());

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(50L, evento.envioId());
        assertEquals(EstadoEnvio.CREADO, evento.estadoAnterior());
        assertEquals(EstadoEnvio.CANCELADO, evento.estadoNuevo());
    }

    @Test
    @DisplayName("Transicion invalida: CREADO -> EN_RUTA lanza TransicionEstadoInvalidaException")
    void testTransicionInvalida() {
        Envio envio = crearEnvioMock(40L, "RE-TEST-005", EstadoEnvio.CREADO);
        when(envioRepository.findById(40L)).thenReturn(Optional.of(envio));

        assertThrows(TransicionEstadoInvalidaException.class, () ->
                envioService.actualizarEstado(40L, EstadoEnvio.EN_RUTA)
        );

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(envioRepository, never()).save(any());
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }
}
