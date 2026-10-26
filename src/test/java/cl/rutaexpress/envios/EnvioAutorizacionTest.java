package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Region;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import cl.rutaexpress.envios.repository.RegionRepository;
import cl.rutaexpress.envios.exception.AccesoDenegadoException;
import cl.rutaexpress.envios.service.EnvioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EnvioAutorizacionTest {

    private static final String SECRETO_VALIDO = "test-secreto-interno-envios-seguro-32caracteres";
    private static final String OID_CLIENTE_A = "11111111-aaaa-bbbb-cccc-000000000001";
    private static final String OID_CLIENTE_B = "22222222-aaaa-bbbb-cccc-000000000002";
    private static final String OID_ADMIN = "99999999-aaaa-bbbb-cccc-000000000009";
    private static final String OID_OPERADOR = "33333333-aaaa-bbbb-cccc-000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnvioService envioService;

    @Autowired
    private EnvioRepository envioRepository;

    @Autowired
    private ComunaRepository comunaRepository;

    @Autowired
    private RegionRepository regionRepository;

    @MockBean
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @MockBean
    private PublicadorKafkaEnvios publicadorKafkaEnvios;

    @MockBean
    private CatalogoClient catalogoClient;

    private Long idEnvioClienteA;
    private Long idEnvioClienteB;
    private Long idEnvioLegadoSinPropietario;

    @BeforeEach
    void setUp() {
        envioRepository.deleteAll();

        Region region = regionRepository.findById(13).orElseGet(() -> regionRepository.save(new Region(13, "Metropolitana de Santiago")));
        Comuna comunaSantiago = comunaRepository.findById(13101).orElseGet(() -> comunaRepository.save(new Comuna(13101, "Santiago", region)));
        Comuna comunaProvidencia = comunaRepository.findById(13123).orElseGet(() -> comunaRepository.save(new Comuna(13123, "Providencia", region)));

        // 1. Envio perteneciente al Cliente A
        Envio envioA = new Envio("RTX-TEST-001", "11111111-1", "22222222-2",
                "Calle A", "100", null, comunaSantiago,
                "Av B", "200", null, comunaProvidencia,
                new BigDecimal("2.50"), EstadoEnvio.CREADO);
        envioA.setCreadoPorOid(OID_CLIENTE_A);
        envioA.setCreadoPorEmail("clientea@empresa.cl");
        idEnvioClienteA = envioRepository.save(envioA).getId();

        // 2. Envio perteneciente al Cliente B
        Envio envioB = new Envio("RTX-TEST-002", "33333333-3", "44444444-4",
                "Calle C", "300", null, comunaSantiago,
                "Av D", "400", null, comunaProvidencia,
                new BigDecimal("3.50"), EstadoEnvio.CREADO);
        envioB.setCreadoPorOid(OID_CLIENTE_B);
        envioB.setCreadoPorEmail("clienteb@empresa.cl");
        idEnvioClienteB = envioRepository.save(envioB).getId();

        // 3. Envio legado sin propietario (D3)
        Envio envioLegado = new Envio("RTX-TEST-003", "55555555-5", "66666666-6",
                "Calle E", "500", null, comunaSantiago,
                "Av F", "600", null, comunaProvidencia,
                new BigDecimal("1.20"), EstadoEnvio.CREADO);
        envioLegado.setCreadoPorOid(null);
        envioLegado.setCreadoPorEmail(null);
        idEnvioLegadoSinPropietario = envioRepository.save(envioLegado).getId();
    }

    // -------------------------------------------------------------------------
    // 1. Pruebas de Autenticación Interna (Secreto X-Servicio-Interno)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Llamada a /api/envios sin cabecera X-Servicio-Interno retorna 401 Unauthorized")
    void llamadaSinSecretoInternoRetorna401() throws Exception {
        mockMvc.perform(get("/api/envios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Llamada a /api/envios con cabecera X-Servicio-Interno invalida retorna 401 Unauthorized")
    void llamadaConSecretoInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", "secreto-falso-atacante")
                        .header("X-Usuario-Oid", OID_ADMIN)
                        .header("X-Usuario-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Llamada a endpoint publico de seguimiento no exige secreto y responde 200 OK")
    void endpointPublicoSeguimientoSinSecreto() throws Exception {
        mockMvc.perform(get("/api/envios/publico/seguimiento/RTX-TEST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento", is("RTX-TEST-001")));
    }

    @Test
    @DisplayName("Ruta protegida sin cabecera X-Usuario-Oid retorna 401 Unauthorized")
    void llamadaSinOidDeUsuarioRetorna401() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 2. Control de Acceso y Propiedad: Listar Envíos
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rol CLIENTE solo lista sus propios envios (no ve los de Cliente B ni los legados)")
    void clienteSoloListaSusPropiosEnvios() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Email", "clientea@empresa.cl")
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(idEnvioClienteA.intValue())))
                .andExpect(jsonPath("$[0].codigoSeguimiento", is("RTX-TEST-001")));
    }

    @Test
    @DisplayName("Rol ADMIN lista todos los envios (propios, de terceros y legados sin propietario)")
    void adminListaTodosLosEnvios() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_ADMIN)
                        .header("X-Usuario-Email", "admin@rutaexpress.cl")
                        .header("X-Usuario-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Rol DESPACHADOR lista todos los envios")
    void despachadorListaTodosLosEnvios() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", "oid-despachador")
                        .header("X-Usuario-Roles", "DESPACHADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Rol OPERADOR lista todos los envios en modo consulta (D5)")
    void operadorListaTodosLosEnvios() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_OPERADOR)
                        .header("X-Usuario-Roles", "OPERADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Rol AUDITOR recibe 403 Forbidden al intentar listar envios")
    void auditorNoPuedeListarEnvios() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", "oid-auditor")
                        .header("X-Usuario-Roles", "AUDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", notNullValue()));
    }

    // -------------------------------------------------------------------------
    // 3. Control de Acceso: Obtener Envío por ID (Decisión D4)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cliente A puede obtener su propio envio por ID")
    void clienteObtieneSuPropioEnvio() throws Exception {
        mockMvc.perform(get("/api/envios/" + idEnvioClienteA)
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idEnvioClienteA.intValue())))
                .andExpect(jsonPath("$.creadoPorEmail", is("clientea@empresa.cl")));
    }

    @Test
    @DisplayName("Cliente A intenta acceder al envio de Cliente B y recibe 403 Forbidden con mensaje generico (D4)")
    void clienteAccedeAEnvioAjenoRetorna403() throws Exception {
        mockMvc.perform(get("/api/envios/" + idEnvioClienteB)
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("No tiene acceso a este envío")));
    }

    @Test
    @DisplayName("Cliente A intenta acceder a envio legado sin propietario y recibe 403 Forbidden")
    void clienteAccedeAEnvioLegadoRetorna403() throws Exception {
        mockMvc.perform(get("/api/envios/" + idEnvioLegadoSinPropietario)
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("No tiene acceso a este envío")));
    }

    @Test
    @DisplayName("Cliente solicita un ID inexistente y recibe 404 Not Found")
    void clienteSolicitaIdInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/envios/999999")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // 4. Creación de Envío y Registro de Propietario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Al crear envio con CLIENTE se persiste creadoPorOid y creadoPorEmail")
    void crearEnvioRegistraPropietario() throws Exception {
        CrearEnvioRequest request = TestEnvioFactory.crearRequestValido("15678901-2", "18234567-8", new BigDecimal("4.20"));

        String respuestaJson = mockMvc.perform(post("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Email", "clientea@empresa.cl")
                        .header("X-Usuario-Roles", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadoPorEmail", is("clientea@empresa.cl")))
                .andReturn().getResponse().getContentAsString();

        Long idCreado = objectMapper.readTree(respuestaJson).get("id").asLong();
        Envio envioPersistido = envioRepository.findById(idCreado).orElseThrow();
        assertEquals(OID_CLIENTE_A, envioPersistido.getCreadoPorOid());
        assertEquals("clientea@empresa.cl", envioPersistido.getCreadoPorEmail());
    }

    @Test
    @DisplayName("Rol OPERADOR recibe 403 Forbidden al intentar crear un envio")
    void operadorNoPuedeCrearEnvio() throws Exception {
        CrearEnvioRequest request = TestEnvioFactory.crearRequestValido("15678901-2", "18234567-8", new BigDecimal("1.00"));

        mockMvc.perform(post("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_OPERADOR)
                        .header("X-Usuario-Roles", "OPERADOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("No tiene permisos para crear envíos")));
    }

    // -------------------------------------------------------------------------
    // 5. Cambio de Estado y Generación de Etiqueta
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rol CLIENTE recibe 403 Forbidden al intentar cambiar estado")
    void clienteNoPuedeActualizarEstado() throws Exception {
        mockMvc.perform(put("/api/envios/" + idEnvioClienteA + "/estado")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("No tiene permisos para modificar el estado del envío")));
    }

    @Test
    @DisplayName("Rol CLIENTE recibe 403 Forbidden al solicitar etiqueta")
    void clienteNoPuedeSolicitarEtiqueta() throws Exception {
        mockMvc.perform(post("/api/envios/" + idEnvioClienteA + "/etiqueta")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("No tiene permisos para solicitar la etiqueta del envío")));
    }

    @Test
    @DisplayName("Rol OPERADOR puede solicitar etiqueta (202 Accepted, D5)")
    void operadorPuedeSolicitarEtiqueta() throws Exception {
        mockMvc.perform(post("/api/envios/" + idEnvioClienteA + "/etiqueta")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_OPERADOR)
                        .header("X-Usuario-Roles", "OPERADOR"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Usuario con multiples roles (ADMIN y CLIENTE) recibe el acceso mas amplio")
    void usuarioConMultiplesRolesRecibeAccesoMasAmplio() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_CLIENTE_A)
                        .header("X-Usuario-Roles", "CLIENTE,ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // -------------------------------------------------------------------------
    // 6. Directivas Fase 3: Fallar Cerrado (A-1) y Rechazo de Rol No Permitido (A-2)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A-1: EnvioService falla cerrado si usuario es null en todos sus metodos publicos")
    void servicioFallaCerradoSiUsuarioEsNull() {
        CrearEnvioRequest req = TestEnvioFactory.crearRequestValido();

        AccesoDenegadoException exCrear = assertThrows(AccesoDenegadoException.class, () ->
                envioService.crear(req, null));
        assertEquals("Identidad de usuario requerida", exCrear.getMessage());

        AccesoDenegadoException exListar = assertThrows(AccesoDenegadoException.class, () ->
                envioService.listar(null, null, null, null));
        assertEquals("Identidad de usuario requerida", exListar.getMessage());

        AccesoDenegadoException exObtener = assertThrows(AccesoDenegadoException.class, () ->
                envioService.obtenerPorId(idEnvioClienteA, null));
        assertEquals("Identidad de usuario requerida", exObtener.getMessage());

        AccesoDenegadoException exActualizar = assertThrows(AccesoDenegadoException.class, () ->
                envioService.actualizarEstado(idEnvioClienteA, EstadoEnvio.ACEPTADO, null));
        assertEquals("Identidad de usuario requerida", exActualizar.getMessage());

        AccesoDenegadoException exEtiqueta = assertThrows(AccesoDenegadoException.class, () ->
                envioService.solicitarGeneracionEtiqueta(idEnvioClienteA, null));
        assertEquals("Identidad de usuario requerida", exEtiqueta.getMessage());
    }

    @Test
    @DisplayName("A-2: Rol inventado ADMINISTRADOR no otorga acceso y es rechazado con 403 Forbidden")
    void rolInventadoAdministradorEsRechazado() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO_VALIDO)
                        .header("X-Usuario-Oid", OID_ADMIN)
                        .header("X-Usuario-Roles", "ADMINISTRADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje", is("Rol no autorizado para acceder al listado de envíos")));
    }
}
