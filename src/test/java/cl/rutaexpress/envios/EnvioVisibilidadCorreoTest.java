package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnvioVisibilidadCorreoTest {

    private static final String SECRETO = "test-secreto-interno-envios-seguro-32caracteres";
    private static final String OID_STAFF = "99999999-aaaa-bbbb-cccc-000000000009";
    private static final String OID_CLIENTE = "11111111-aaaa-bbbb-cccc-000000000001";

    @Autowired
    private MockMvc mockMvc;

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

    private Long idX;
    private Long idY;
    private Long idZ;
    private Long idW;

    @BeforeEach
    void setUp() {
        envioRepository.deleteAll();
        // X: del personal, remitente con el correo del cliente; Y: mismo usuario pero dominio .com;
        // Z: otro usuario; W: creado por el propio cliente, sin correo de remitente
        idX = persistir("RTX-VIS-X", OID_STAFF, "ana.perez@empresa.cl");
        idY = persistir("RTX-VIS-Y", OID_STAFF, "ana.perez@empresa.com");
        idZ = persistir("RTX-VIS-Z", OID_STAFF, "otro@empresa.cl");
        idW = persistir("RTX-VIS-W", OID_CLIENTE, null);
    }

    private Long persistir(String codigo, String oidCreador, String remitenteEmail) {
        Comuna origen = comunaRepository.findById(13123).orElseThrow();
        Comuna destino = comunaRepository.findById(13101).orElseThrow();
        Envio envio = new Envio(codigo, "11111111-1", "22222222-2",
                "Calle A", "100", null, origen,
                "Calle B", "200", null, destino,
                new BigDecimal("2.00"), EstadoEnvio.CREADO);
        envio.setCreadoPorOid(oidCreador);
        envio.setRemitenteEmail(remitenteEmail);
        return envioRepository.save(envio).getId();
    }

    private MockHttpServletRequestBuilder comoCliente(MockHttpServletRequestBuilder builder, String correo) {
        builder.header("X-Servicio-Interno", SECRETO)
                .header("X-Usuario-Oid", OID_CLIENTE)
                .header("X-Usuario-Roles", "CLIENTE");
        if (correo != null) {
            builder.header("X-Usuario-Email", correo);
        }
        return builder;
    }

    @Test
    @DisplayName("C23 y C24: otra capitalizacion y espacios externos coinciden; .com y otro usuario no")
    void correoConOtraCapitalizacionCoincide() throws Exception {
        String correo = "  ANA.Perez@Empresa.CL ";

        mockMvc.perform(comoCliente(get("/api/envios"), correo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(idX.intValue(), idW.intValue())));

        mockMvc.perform(comoCliente(get("/api/envios/" + idX), correo)).andExpect(status().isOk());
        mockMvc.perform(comoCliente(get("/api/envios/" + idY), correo)).andExpect(status().isForbidden());
        mockMvc.perform(comoCliente(get("/api/envios/" + idZ), correo)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("C25: token sin correo solo ve por oid; un id inexistente sigue siendo 404")
    void tokenSinCorreoSoloVePorOid() throws Exception {
        mockMvc.perform(comoCliente(get("/api/envios"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(idW.intValue()));

        mockMvc.perform(comoCliente(get("/api/envios/" + idX), null)).andExpect(status().isForbidden());
        mockMvc.perform(comoCliente(get("/api/envios/999999"), null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un correo invalido en el token no produce error: solo se ve por oid")
    void correoInvalidoEnTokenSoloVePorOid() throws Exception {
        mockMvc.perform(comoCliente(get("/api/envios"), "no es un correo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(idW.intValue()));
    }

    @Test
    @DisplayName("Un envio sin correo de remitente no coincide con ningun correo del token")
    void remitenteNuloNoCoincide() throws Exception {
        // W no tiene remitente_email: un cliente distinto con correo valido no lo ve
        mockMvc.perform(get("/api/envios/" + idW)
                        .header("X-Servicio-Interno", SECRETO)
                        .header("X-Usuario-Oid", "22222222-aaaa-bbbb-cccc-000000000002")
                        .header("X-Usuario-Email", "otro.cliente@empresa.cl")
                        .header("X-Usuario-Roles", "CLIENTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El personal con vista total sigue viendo todos los envios")
    void personalVeTodos() throws Exception {
        mockMvc.perform(get("/api/envios")
                        .header("X-Servicio-Interno", SECRETO)
                        .header("X-Usuario-Oid", OID_STAFF)
                        .header("X-Usuario-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }
}
