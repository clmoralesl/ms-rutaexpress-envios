package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.AceptarEnvioRequest;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoRequest;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnvioAceptacionTest {

    private static final String SECRETO = "test-secreto-interno-envios-seguro-32caracteres";
    private static final String OID_DESPACHADOR = "00000000-0000-0000-0000-despachador1";
    private static final String OID_CLIENTE = "11111111-aaaa-bbbb-cccc-000000000001";
    private static final String OID_OPERADOR = "33333333-aaaa-bbbb-cccc-000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnvioRepository envioRepository;

    @Autowired
    private ComunaRepository comunaRepository;

    @MockBean
    private CatalogoClient catalogoClient;

    @MockBean
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @MockBean
    private PublicadorKafkaEnvios publicadorKafkaEnvios;

    @BeforeEach
    void setUp() {
        envioRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder comoDespachador(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Servicio-Interno", SECRETO)
                .header("X-Usuario-Oid", OID_DESPACHADOR)
                .header("X-Usuario-Email", "Despachador@RutaExpress.cl")
                .header("X-Usuario-Roles", "DESPACHADOR");
    }

    private ResultActions aceptar(Long id, AceptarEnvioRequest solicitud) throws Exception {
        return mockMvc.perform(comoDespachador(post("/api/envios/" + id + "/aceptacion"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)));
    }

    private Long persistirSinServicio(EstadoEnvio estado) {
        Comuna origen = comunaRepository.findById(13123).orElseThrow();
        Comuna destino = comunaRepository.findById(13101).orElseThrow();
        Envio envio = new Envio("RTX-LEG-" + System.nanoTime() % 100000, "11111111-1", "22222222-2",
                "Calle A", "100", null, origen,
                "Calle B", "200", null, destino,
                new BigDecimal("2.00"), estado);
        envio.setCreadoPorOid(OID_DESPACHADOR);
        return envioRepository.save(envio).getId();
    }

    @Test
    @DisplayName("C9: la aceptacion corrige peso y medidas, recalcula el precio y publica declarado y verificado")
    void aceptacionCorrigeYRecalcula() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(2L, 1L, 7000L));

        aceptar(id, new AceptarEnvioRequest(new BigDecimal("4.50"), 30, 20, 10, "caja mas pesada de lo declarado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACEPTADO")));

        ArgumentCaptor<CotizacionCatalogoRequest> captor = ArgumentCaptor.forClass(CotizacionCatalogoRequest.class);
        verify(catalogoClient).cotizar(captor.capture());
        assertEquals(0, new BigDecimal("4.50").compareTo(captor.getValue().pesoKg()));
        assertEquals(Boolean.FALSE, captor.getValue().validarSucursalActiva());
        assertEquals(2L, captor.getValue().servicioId());
        assertEquals(30, captor.getValue().largoCm());

        Envio guardado = envioRepository.findById(id).orElseThrow();
        assertEquals(EstadoEnvio.ACEPTADO, guardado.getEstado());
        assertEquals(7000L, guardado.getPrecioFinal().totalClp());
        assertEquals(6000L, guardado.getPrecioEstimado().totalClp());
        assertEquals(0, new BigDecimal("4.50").compareTo(guardado.getPesoVerificadoKg()));
        assertEquals("despachador@rutaexpress.cl", guardado.getVerificadoPorEmail());
        assertNotNull(guardado.getFechaAceptacion());

        ArgumentCaptor<EventoEnvioKafka> evento = ArgumentCaptor.forClass(EventoEnvioKafka.class);
        verify(publicadorKafkaEnvios).publicarEvento(evento.capture());
        Map<String, Object> detalles = evento.getValue().detalles();
        assertEquals(0, new BigDecimal("3.00").compareTo((BigDecimal) detalles.get("pesoDeclaradoKg")));
        assertEquals(0, new BigDecimal("4.50").compareTo((BigDecimal) detalles.get("pesoVerificadoKg")));
        assertEquals(Boolean.TRUE, detalles.get("corregido"));
        assertEquals(7000L, detalles.get("precioFinalClp"));
        assertEquals(6000L, detalles.get("precioEstimadoClp"));
        assertEquals("caja mas pesada de lo declarado", detalles.get("observacion"));
    }

    @Test
    @DisplayName("Aceptacion sin medidas usa las declaradas y, si el peso no cambia, el evento no marca correccion")
    void aceptacionSinMedidasUsaLasDeclaradas() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(2L, 1L, 6000L));

        aceptar(id, new AceptarEnvioRequest(new BigDecimal("3.00"), null, null, null, null))
                .andExpect(status().isOk());

        ArgumentCaptor<CotizacionCatalogoRequest> captor = ArgumentCaptor.forClass(CotizacionCatalogoRequest.class);
        verify(catalogoClient).cotizar(captor.capture());
        assertEquals(30, captor.getValue().largoCm());
        assertEquals(20, captor.getValue().anchoCm());
        assertEquals(10, captor.getValue().altoCm());

        ArgumentCaptor<EventoEnvioKafka> evento = ArgumentCaptor.forClass(EventoEnvioKafka.class);
        verify(publicadorKafkaEnvios).publicarEvento(evento.capture());
        assertEquals(Boolean.FALSE, evento.getValue().detalles().get("corregido"));
    }

    @Test
    @DisplayName("C3: medidas incompletas al aceptar responde 400 y no consulta el catalogo")
    void medidasIncompletasResponde400() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);

        aceptar(id, new AceptarEnvioRequest(new BigDecimal("3.00"), 30, null, 10, null))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(catalogoClient);
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("AJU1: aceptar sin pesoVerificadoKg o sin cuerpo responde 400")
    void sinPesoVerificadoResponde400() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);

        aceptar(id, new AceptarEnvioRequest(null, 30, 20, 10, null)).andExpect(status().isBadRequest());

        mockMvc.perform(comoDespachador(post("/api/envios/" + id + "/aceptacion"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("C10: cada envio descuenta el cupo de su propio servicio")
    void cadaEnvioDescuentaSuServicio() throws Exception {
        Long idServicio2 = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        Long idServicio5 = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenAnswer(inv -> {
            CotizacionCatalogoRequest r = inv.getArgument(0);
            return TestEnvioFactory.cotizacion(r.servicioId(), 1L, 7000L);
        });

        aceptar(idServicio2, TestEnvioFactory.aceptacionValida()).andExpect(status().isOk());
        aceptar(idServicio5, TestEnvioFactory.aceptacionValida()).andExpect(status().isOk());

        verify(catalogoClient, times(1)).descontarCapacidad(2L);
        verify(catalogoClient, times(1)).descontarCapacidad(5L);
        verify(catalogoClient, never()).descontarCapacidad(1L);
    }

    @Test
    @DisplayName("C16: catalogo caido al aceptar: 503, sigue CREADO y sin descuento")
    void catalogoNoDisponibleNoAcepta() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenThrow(new CatalogoNoDisponibleException("caido"));

        aceptar(id, TestEnvioFactory.aceptacionValida()).andExpect(status().isServiceUnavailable());

        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
        verify(catalogoClient, never()).descontarCapacidad(any());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("C17: envio antiguo en CREADO sin servicio responde 409 ENVIO_SIN_SERVICIO")
    void envioSinServicioResponde409() throws Exception {
        Long id = persistirSinServicio(EstadoEnvio.CREADO);

        aceptar(id, TestEnvioFactory.aceptacionValida())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ENVIO_SIN_SERVICIO")));

        verify(catalogoClient, never()).descontarCapacidad(any());
    }

    @Test
    @DisplayName("C18: aceptar un envio ya aceptado responde 400 y no descuenta otro cupo")
    void envioYaAceptadoResponde400() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(2L, 1L, 7000L));

        aceptar(id, TestEnvioFactory.aceptacionValida()).andExpect(status().isOk());
        aceptar(id, TestEnvioFactory.aceptacionValida()).andExpect(status().isBadRequest());

        verify(catalogoClient, times(1)).descontarCapacidad(2L);
    }

    @Test
    @DisplayName("C19: CLIENTE y OPERADOR reciben 403; un id inexistente responde 404")
    void rolesSinPermisoResponden403() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        String cuerpo = objectMapper.writeValueAsString(TestEnvioFactory.aceptacionValida());

        mockMvc.perform(post("/api/envios/" + id + "/aceptacion")
                        .header("X-Servicio-Interno", SECRETO)
                        .header("X-Usuario-Oid", OID_CLIENTE)
                        .header("X-Usuario-Roles", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/envios/" + id + "/aceptacion")
                        .header("X-Servicio-Interno", SECRETO)
                        .header("X-Usuario-Oid", OID_OPERADOR)
                        .header("X-Usuario-Roles", "OPERADOR")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());

        verifyNoInteractions(catalogoClient);
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("C19: aceptar un envio inexistente responde 404")
    void inexistenteResponde404() throws Exception {
        aceptar(999999L, TestEnvioFactory.aceptacionValida()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("C20: PUT estado con ACEPTADO responde 400 con la ruta correcta y no toca el catalogo")
    void putEstadoAceptadoResponde400() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);

        mockMvc.perform(comoDespachador(put("/api/envios/" + id + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACEPTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("/aceptacion")));

        verifyNoInteractions(catalogoClient);
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("C21: EN_BODEGA con servicio y sin precio final responde 409 ENVIO_SIN_PRECIO_FINAL")
    void enBodegaSinPrecioFinalResponde409() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        Envio envio = envioRepository.findById(id).orElseThrow();
        envio.setEstado(EstadoEnvio.ACEPTADO);
        envioRepository.save(envio);

        mockMvc.perform(comoDespachador(put("/api/envios/" + id + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_BODEGA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("ENVIO_SIN_PRECIO_FINAL")));

        assertEquals(EstadoEnvio.ACEPTADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("C22: un envio antiguo (sin servicio) en ACEPTADO sigue avanzando a EN_BODEGA")
    void envioAntiguoSigueAvanzando() throws Exception {
        Long id = persistirSinServicio(EstadoEnvio.ACEPTADO);

        mockMvc.perform(comoDespachador(put("/api/envios/" + id + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_BODEGA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_BODEGA")));
    }

    @Test
    @DisplayName("Tras aceptar, el envio con precio final avanza a EN_BODEGA")
    void envioAceptadoConPrecioFinalAvanza() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(2L, 1L, 7000L));

        aceptar(id, TestEnvioFactory.aceptacionValida()).andExpect(status().isOk());

        mockMvc.perform(comoDespachador(put("/api/envios/" + id + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_BODEGA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_BODEGA")));
    }
}
