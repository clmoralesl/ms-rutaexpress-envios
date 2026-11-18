package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoRequest;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
import cl.rutaexpress.envios.exception.CotizacionRechazadaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnvioPreregistroTest {

    private static final String SECRETO = "test-secreto-interno-envios-seguro-32caracteres";
    private static final String OID_ADMIN = "99999999-aaaa-bbbb-cccc-000000000009";
    private static final String OID_CLIENTE = "11111111-aaaa-bbbb-cccc-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnvioRepository envioRepository;

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

    private Map<String, Object> cuerpo(CrearEnvioRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mapa = objectMapper.convertValue(request, Map.class);
        return mapa;
    }

    private ResultActions crearComoAdmin(Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(conCabeceras(post("/api/envios"), OID_ADMIN, "admin@rutaexpress.cl", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private MockHttpServletRequestBuilder conCabeceras(MockHttpServletRequestBuilder builder, String oid, String email, String roles) {
        builder.header("X-Servicio-Interno", SECRETO)
                .header("X-Usuario-Oid", oid)
                .header("X-Usuario-Roles", roles);
        if (email != null) {
            builder.header("X-Usuario-Email", email);
        }
        return builder;
    }

    @Test
    @DisplayName("C1: crear sin sucursalOrigenId o sin servicioId responde 400 y no consulta el catalogo")
    void sinSucursalOServicioResponde400() throws Exception {
        Map<String, Object> sinSucursal = cuerpo(TestEnvioFactory.crearRequestValido());
        sinSucursal.remove("sucursalOrigenId");
        crearComoAdmin(sinSucursal).andExpect(status().isBadRequest());

        Map<String, Object> sinServicio = cuerpo(TestEnvioFactory.crearRequestValido());
        sinServicio.remove("servicioId");
        crearComoAdmin(sinServicio).andExpect(status().isBadRequest());

        assertEquals(0, envioRepository.count());
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("C2: el catalogo rechaza (servicio o sucursal inactivos, comuna no cubierta): 422 y ningun envio creado")
    void servicioRechazadoPorCatalogoResponde422SinCrear() throws Exception {
        when(catalogoClient.cotizar(any()))
                .thenThrow(new CotizacionRechazadaException(422, "SERVICIO_INACTIVO", "El servicio esta inactivo"));
        long antes = envioRepository.count();

        crearComoAdmin(cuerpo(TestEnvioFactory.crearRequestValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("SERVICIO_INACTIVO")));

        assertEquals(antes, envioRepository.count());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("C3: medidas incompletas al crear responde 400")
    void medidasIncompletasResponde400() throws Exception {
        Map<String, Object> incompletas = cuerpo(TestEnvioFactory.crearRequestValido());
        incompletas.put("largoCm", 30);
        incompletas.put("anchoCm", 20);

        crearComoAdmin(incompletas).andExpect(status().isBadRequest());

        assertEquals(0, envioRepository.count());
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("C4: un precio enviado en el cuerpo se ignora y se guarda el del catalogo")
    void precioDelCuerpoSeIgnora() throws Exception {
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(7L, 1L, 5000L));
        Map<String, Object> conPrecio = cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 7L, 1L, "remitente@empresa.cl"));
        conPrecio.put("precioEstimadoClp", 1);

        String json = crearComoAdmin(conPrecio)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        Envio guardado = envioRepository.findById(id).orElseThrow();
        assertNotNull(guardado.getPrecioEstimado());
        assertEquals(5000L, guardado.getPrecioEstimado().totalClp());
    }

    @Test
    @DisplayName("C5: el origen se copia de la sucursal, se guarda el servicio y crear no descuenta cupo")
    void origenSeCopiaDeLaSucursal() throws Exception {
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(7L, 3L, 5000L));

        String json = crearComoAdmin(cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 7L, 3L, "remitente@empresa.cl")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        Envio guardado = envioRepository.findById(id).orElseThrow();
        assertEquals("Av. Providencia", guardado.getCalleOrigen());
        assertEquals("2045", guardado.getNumeroOrigen());
        assertEquals("Local 4", guardado.getComplementoOrigen());
        assertEquals(13123, guardado.getComunaOrigen().getIdComuna());
        assertEquals(7L, guardado.getServicioId());
        assertEquals(3L, guardado.getSucursalOrigenId());

        ArgumentCaptor<CotizacionCatalogoRequest> captor = ArgumentCaptor.forClass(CotizacionCatalogoRequest.class);
        verify(catalogoClient).cotizar(captor.capture());
        assertEquals(7L, captor.getValue().servicioId());
        assertEquals(3L, captor.getValue().sucursalOrigenId());
        assertEquals(13101, captor.getValue().idComunaDestino());
        assertEquals(Boolean.TRUE, captor.getValue().validarSucursalActiva());

        verify(catalogoClient, never()).descontarCapacidad(any());
    }

    @Test
    @DisplayName("C6: catalogo caido o lento al crear: 503 y ningun envio creado")
    void catalogoNoDisponibleResponde503SinCrear() throws Exception {
        when(catalogoClient.cotizar(any())).thenThrow(new CatalogoNoDisponibleException("tiempo agotado"));
        long antes = envioRepository.count();

        crearComoAdmin(cuerpo(TestEnvioFactory.crearRequestValido()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("CATALOGO_NO_DISPONIBLE")));

        assertEquals(antes, envioRepository.count());
        verify(publicadorKafkaEnvios, never()).publicarEvento(any());
    }

    @Test
    @DisplayName("C7: un CLIENTE no puede fijar remitenteEmail; se usa el correo normalizado de su token")
    void clienteNoPuedeFijarRemitenteEmail() throws Exception {
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(1L, 1L, 5000L));
        Map<String, Object> cuerpo = cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 1L, 1L, "otro@x.cl"));

        String json = mockMvc.perform(conCabeceras(post("/api/envios"), OID_CLIENTE, "Cliente.A@Empresa.cl", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        Envio guardado = envioRepository.findById(id).orElseThrow();
        assertEquals("cliente.a@empresa.cl", guardado.getRemitenteEmail());
        assertEquals("Cliente.A@Empresa.cl", guardado.getCreadoPorEmail());
    }

    @Test
    @DisplayName("C8: el personal sin remitenteEmail, o con uno invalido, recibe 400 sin consultar el catalogo")
    void personalSinRemitenteEmailResponde400() throws Exception {
        Map<String, Object> sinCampo = cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 1L, 1L, null));
        crearComoAdmin(sinCampo).andExpect(status().isBadRequest());

        Map<String, Object> invalido = cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 1L, 1L, "ana perez@x.cl"));
        crearComoAdmin(invalido).andExpect(status().isBadRequest());

        assertEquals(0, envioRepository.count());
        verify(catalogoClient, never()).cotizar(any());
    }

    @Test
    @DisplayName("El personal informa un remitenteEmail y se guarda normalizado")
    void personalFijaRemitenteEmailNormalizado() throws Exception {
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(1L, 1L, 5000L));
        Map<String, Object> cuerpo = cuerpo(TestEnvioFactory.crearRequest(
                "11111111-1", "22222222-2", new BigDecimal("3.00"), 1L, 1L, "  Ana.Perez@EMPRESA.cl "));

        String json = crearComoAdmin(cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        assertEquals("ana.perez@empresa.cl", envioRepository.findById(id).orElseThrow().getRemitenteEmail());
    }
}
