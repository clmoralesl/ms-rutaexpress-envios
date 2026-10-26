package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.TestEnvioFactory;
import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.dto.NotificacionEmailPayload;
import cl.rutaexpress.envios.dto.SeguimientoPublicoResponse;
import cl.rutaexpress.envios.dto.TicketBodegaPayload;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Region;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.ComunaNoEncontradaException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import cl.rutaexpress.envios.repository.RegionRepository;
import cl.rutaexpress.envios.testutil.UsuariosPrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
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
    private ComunaRepository comunaRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private GeneradorCodigoSeguimiento generadorCodigoSeguimiento;

    @Spy
    private NormalizadorDireccion normalizadorDireccion = new NormalizadorDireccion();

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

    private Region regionMetropolitana;
    private Comuna comunaSantiago;
    private Comuna comunaProvidencia;

    @BeforeEach
    void setUp() {
        regionMetropolitana = new Region(13, "Metropolitana de Santiago");
        comunaSantiago = new Comuna(13101, "Santiago", regionMetropolitana);
        comunaProvidencia = new Comuna(13123, "Providencia", regionMetropolitana);

        lenient().when(generadorCodigoSeguimiento.generar()).thenReturn("RTX-2026-TEST01");
        lenient().when(comunaRepository.findById(13101)).thenReturn(Optional.of(comunaSantiago));
        lenient().when(comunaRepository.findById(13123)).thenReturn(Optional.of(comunaProvidencia));

        envioService = new EnvioService(
                envioRepository,
                comunaRepository,
                regionRepository,
                generadorCodigoSeguimiento,
                normalizadorDireccion,
                catalogoClient,
                publicadorRabbitMQ,
                publicadorKafkaEnvios
        );
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
        CrearEnvioRequest request = TestEnvioFactory.crearRequestValido("11111111-1", "22222222-2", new BigDecimal("3.00"));

        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> {
            Envio guardado = invocation.getArgument(0);
            var field = Envio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(guardado, 99L);
            return guardado;
        });

        EnvioResponse response = envioService.crear(request, UsuariosPrueba.admin());

        assertNotNull(response);
        assertEquals(99L, response.id());
        assertEquals("RTX-2026-TEST01", response.codigoSeguimiento());
        assertEquals(EstadoEnvio.CREADO, response.estado());

        verify(envioRepository, times(1)).save(any(Envio.class));
        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());

        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(99L, evento.envioId());
        assertEquals("RTX-2026-TEST01", evento.codigoSeguimiento());
        assertNull(evento.estadoAnterior());
        assertEquals(EstadoEnvio.CREADO, evento.estadoNuevo());
        assertEquals("admin@rutaexpress.cl", evento.usuarioEmail());
        assertEquals("ADMIN", evento.usuarioRol());
        assertEquals(new BigDecimal("3.00"), evento.detalles().get("pesoKg"));
    }

    @Test
    @DisplayName("Transicion a ACEPTADO: Descuenta capacidad en catalogo, publica en RabbitMQ y Kafka")
    void testTransicionAceptadoExito() {
        Envio envio = crearEnvioMock(10L, "RE-TEST-001", EstadoEnvio.CREADO);
        when(envioRepository.findById(10L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(10L, EstadoEnvio.ACEPTADO, 2L, UsuariosPrueba.admin());

        assertEquals(EstadoEnvio.ACEPTADO, response.estado());

        verify(catalogoClient, times(1)).descontarCapacidad(2L);

        verify(publicadorRabbitMQ, times(2)).publicar(any(), any(), mensajeCaptor.capture());
        var mensajes = mensajeCaptor.getAllValues();
        assertEquals(2, mensajes.size());

        boolean tieneEmail = mensajes.stream().anyMatch(m -> "EMAIL_ENVIO_ACEPTADO".equals(m.tipo()));
        boolean tieneTicket = mensajes.stream().anyMatch(m -> "TICKET_BODEGA_GENERAR".equals(m.tipo()));
        assertTrue(tieneEmail);
        assertTrue(tieneTicket);

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        EventoEnvioKafka evento = eventoKafkaCaptor.getValue();
        assertEquals(10L, evento.envioId());
        assertEquals(EstadoEnvio.CREADO, evento.estadoAnterior());
        assertEquals(EstadoEnvio.ACEPTADO, evento.estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a ACEPTADO con capacidad agotada: lanza excepcion y no publica en brokers")
    void testTransicionAceptadoCapacidadAgotada() {
        Envio envio = crearEnvioMock(10L, "RE-TEST-002", EstadoEnvio.CREADO);
        when(envioRepository.findById(10L)).thenReturn(Optional.of(envio));
        doThrow(new CapacidadFlotaAgotadaException(3L)).when(catalogoClient).descontarCapacidad(3L);

        assertThrows(CapacidadFlotaAgotadaException.class, () ->
                envioService.actualizarEstado(10L, EstadoEnvio.ACEPTADO, 3L, UsuariosPrueba.admin())
        );

        verify(envioRepository, never()).save(any());
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("Transicion a EN_RUTA: publica email de despacho y evento en Kafka")
    void testTransicionEnRutaExito() {
        Envio envio = crearEnvioMock(20L, "RE-TEST-003", EstadoEnvio.EN_BODEGA);
        when(envioRepository.findById(20L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(20L, EstadoEnvio.EN_RUTA, UsuariosPrueba.admin());

        assertEquals(EstadoEnvio.EN_RUTA, response.estado());

        verify(catalogoClient, never()).descontarCapacidad(any());

        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                mensajeCaptor.capture()
        );
        assertEquals("EMAIL_ENVIO_EN_RUTA", mensajeCaptor.getValue().tipo());

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        assertEquals(EstadoEnvio.EN_BODEGA, eventoKafkaCaptor.getValue().estadoAnterior());
        assertEquals(EstadoEnvio.EN_RUTA, eventoKafkaCaptor.getValue().estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a ENTREGADO: publica email de entrega final y evento en Kafka")
    void testTransicionEntregadoExito() {
        Envio envio = crearEnvioMock(30L, "RE-TEST-004", EstadoEnvio.EN_RUTA);
        when(envioRepository.findById(30L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(30L, EstadoEnvio.ENTREGADO, UsuariosPrueba.admin());

        assertEquals(EstadoEnvio.ENTREGADO, response.estado());

        verify(publicadorRabbitMQ, times(1)).publicar(
                eq(RabbitMQConfig.EXCHANGE_DIRECT),
                eq(RabbitMQConfig.ROUTING_KEY_EMAIL),
                mensajeCaptor.capture()
        );
        assertEquals("EMAIL_ENVIO_ENTREGADO", mensajeCaptor.getValue().tipo());

        verify(publicadorKafkaEnvios, times(1)).publicarEvento(eventoKafkaCaptor.capture());
        assertEquals(EstadoEnvio.EN_RUTA, eventoKafkaCaptor.getValue().estadoAnterior());
        assertEquals(EstadoEnvio.ENTREGADO, eventoKafkaCaptor.getValue().estadoNuevo());
    }

    @Test
    @DisplayName("Transicion a CANCELADO: no descuenta catalogo, no publica en RabbitMQ, si publica en Kafka")
    void testTransicionCanceladoExito() {
        Envio envio = crearEnvioMock(50L, "RE-TEST-006", EstadoEnvio.CREADO);
        when(envioRepository.findById(50L)).thenReturn(Optional.of(envio));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.actualizarEstado(50L, EstadoEnvio.CANCELADO, UsuariosPrueba.admin());

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
                envioService.actualizarEstado(40L, EstadoEnvio.EN_RUTA, UsuariosPrueba.admin())
        );

        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(envioRepository, never()).save(any());
        verify(publicadorRabbitMQ, never()).publicar(any(), any(), any());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("Rastreo público protege privacidad: contiene comuna y región, y no expone calle ni número ni RUT")
    void testRastreoPublicoProtegePrivacidad() throws Exception {
        Envio envio = crearEnvioMock(10L, "RTX-2026-PRIV01", EstadoEnvio.EN_RUTA);
        when(envioRepository.findByCodigoSeguimiento("RTX-2026-PRIV01")).thenReturn(Optional.of(envio));

        SeguimientoPublicoResponse response = envioService.obtenerSeguimientoPublico("RTX-2026-PRIV01");

        assertNotNull(response);
        assertEquals("RTX-2026-PRIV01", response.codigoSeguimiento());
        assertTrue(response.ciudadOrigen().contains("Santiago"));
        assertTrue(response.ciudadOrigen().contains("Metropolitana"));
        assertTrue(response.ciudadDestino().contains("Providencia"));
        assertTrue(response.ciudadDestino().contains("Metropolitana"));

        // Verificar sobre JSON serializado que no se filtran datos privados
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String json = mapper.writeValueAsString(response);

        assertFalse(json.contains("Av. Providencia"), "No debe contener la calle de origen");
        assertFalse(json.contains("1234"), "No debe contener el número de origen");
        assertFalse(json.contains("Av. Apoquindo"), "No debe contener la calle de destino");
        assertFalse(json.contains("11111111-1"), "No debe contener RUT del remitente");
        assertFalse(json.contains("22222222-2"), "No debe contener RUT del destinatario");
    }

    @Test
    @DisplayName("Comuna inexistente responde ComunaNoEncontradaException (400)")
    void testIdComunaInexistenteLanzaExcepcion() {
        CrearEnvioRequest request = TestEnvioFactory.crearRequestConComunaInexistente(99999);
        when(comunaRepository.findById(99999)).thenReturn(Optional.empty());

        assertThrows(ComunaNoEncontradaException.class, () ->
                envioService.crear(request, UsuariosPrueba.admin()));

        verify(envioRepository, never()).save(any());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("Reintento ante código de seguimiento duplicado hasta encontrar uno libre")
    void testReintentoCodigoDuplicado() {
        CrearEnvioRequest request = TestEnvioFactory.crearRequestValido();

        when(generadorCodigoSeguimiento.generar())
                .thenReturn("RTX-2026-COLL01") // Primera llamada: colisión
                .thenReturn("RTX-2026-FREE02"); // Segunda llamada: libre

        when(envioRepository.existsByCodigoSeguimiento("RTX-2026-COLL01")).thenReturn(true);
        when(envioRepository.existsByCodigoSeguimiento("RTX-2026-FREE02")).thenReturn(false);

        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse response = envioService.crear(request, UsuariosPrueba.admin());

        assertNotNull(response);
        assertEquals("RTX-2026-FREE02", response.codigoSeguimiento());
        verify(generadorCodigoSeguimiento, times(2)).generar();
    }

    @Test
    @DisplayName("Si la persistencia falla, los eventos de Kafka no se publican")
    void testFalloPersistenciaNoPublicaKafka() {
        CrearEnvioRequest request = TestEnvioFactory.crearRequestValido();
        when(envioRepository.save(any(Envio.class))).thenThrow(new RuntimeException("Error fatal en base de datos"));

        assertThrows(RuntimeException.class, () ->
                envioService.crear(request, UsuariosPrueba.admin()));

        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }
}
