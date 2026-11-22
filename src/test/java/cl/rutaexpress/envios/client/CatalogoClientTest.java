package cl.rutaexpress.envios.client;

import cl.rutaexpress.envios.TestEnvioFactory;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoRequest;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoResponse;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
import cl.rutaexpress.envios.exception.CotizacionRechazadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogoClientTest {

    private static final String SECRETO_TEST = "test-secreto-interno-envios-seguro-32caracteres";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private CatalogoClient catalogoClient;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .defaultHeader("X-Servicio-Interno", SECRETO_TEST);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        catalogoClient = new CatalogoClient(restClientBuilder.build());
    }

    @Test
    @DisplayName("Debe descontar capacidad exitosamente incluyendo cabecera X-Servicio-Interno cuando el catalogo responde 200 OK")
    void testDescontarCapacidadExito() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/servicios/1/descontar-capacidad"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Servicio-Interno", SECRETO_TEST))
                .andRespond(withSuccess("{\"id\":1,\"capacidadDisponible\":49}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> catalogoClient.descontarCapacidad(1L));

        mockServer.verify();
    }

    @Test
    @DisplayName("Debe lanzar CapacidadFlotaAgotadaException cuando el catalogo responde 409 Conflict")
    void testDescontarCapacidadAgotada() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/servicios/2/descontar-capacidad"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Servicio-Interno", SECRETO_TEST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("{\"mensaje\":\"Capacidad de flota agotada\"}"));

        assertThrows(CapacidadFlotaAgotadaException.class, () -> catalogoClient.descontarCapacidad(2L));

        mockServer.verify();
    }

    private static final String COTIZACION_OK = "{\"servicioId\":1,\"sucursalOrigenId\":1,\"idComunaOrigen\":13123,"
            + "\"zona\":\"MISMA_REGION\",\"factorZona\":1.15,\"tarifaBase\":3000.00,\"precioPorKg\":500.00,"
            + "\"pesoVolumetricoKg\":1.20,\"pesoFacturableKg\":3.00,\"totalClp\":6000,\"campoNuevoDesconocido\":\"x\","
            + "\"direccionSucursal\":{\"nombre\":\"Providencia\",\"calle\":\"Av. Providencia\",\"numero\":\"2045\","
            + "\"complemento\":\"Local 4\",\"idComuna\":13123}}";

    @Test
    @DisplayName("Cotizar: envia cabecera interna y validarSucursalActiva, y mapea la respuesta ignorando campos desconocidos")
    void testCotizarExito() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Servicio-Interno", SECRETO_TEST))
                .andExpect(jsonPath("$.validarSucursalActiva").value(false))
                .andExpect(jsonPath("$.servicioId").value(1))
                .andRespond(withSuccess(COTIZACION_OK, MediaType.APPLICATION_JSON));

        CotizacionCatalogoResponse respuesta = catalogoClient.cotizar(new CotizacionCatalogoRequest(
                1L, 1L, 13101, new BigDecimal("3.00"), null, false));

        assertEquals(6000L, respuesta.totalClp());
        assertEquals("MISMA_REGION", respuesta.zona());
        assertEquals("Av. Providencia", respuesta.direccionSucursal().calle());
        assertEquals(13123, respuesta.direccionSucursal().idComuna());
        mockServer.verify();
    }

    @Test
    @DisplayName("Cotizar: 422 con codigo se traduce a CotizacionRechazadaException con status 422 y el codigo del catalogo")
    void testCotizarReglaDeNegocio422() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"codigo\":\"SERVICIO_INACTIVO\",\"mensaje\":\"El servicio esta inactivo\"}"));

        CotizacionRechazadaException ex = assertThrows(CotizacionRechazadaException.class,
                () -> catalogoClient.cotizar(TestEnvioFactory.solicitudCotizacion()));

        assertEquals(422, ex.getStatus());
        assertEquals("SERVICIO_INACTIVO", ex.getCodigo());
        assertEquals("El servicio esta inactivo", ex.getMessage());
    }

    @Test
    @DisplayName("Cotizar: 404 (servicio o sucursal inexistente) se traduce a status 422 usando el campo error como codigo")
    void testCotizarNoEncontrado404() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"RECURSO_NO_ENCONTRADO\"}"));

        CotizacionRechazadaException ex = assertThrows(CotizacionRechazadaException.class,
                () -> catalogoClient.cotizar(TestEnvioFactory.solicitudCotizacion()));

        assertEquals(422, ex.getStatus());
        assertEquals("RECURSO_NO_ENCONTRADO", ex.getCodigo());
    }

    @Test
    @DisplayName("Cotizar: 400 conserva el status 400 aunque el cuerpo no sea JSON")
    void testCotizarParametrosInvalidos400() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("no es json"));

        CotizacionRechazadaException ex = assertThrows(CotizacionRechazadaException.class,
                () -> catalogoClient.cotizar(TestEnvioFactory.solicitudCotizacion()));

        assertEquals(400, ex.getStatus());
        assertEquals("COTIZACION_RECHAZADA", ex.getCodigo());
    }

    @Test
    @DisplayName("Cotizar: 500 del catalogo se traduce a CatalogoNoDisponibleException")
    void testCotizarErrorServidor500() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(CatalogoNoDisponibleException.class,
                () -> catalogoClient.cotizar(TestEnvioFactory.solicitudCotizacion()));
    }

    @Test
    @DisplayName("Cotizar: respuesta sin totalClp se trata como catalogo no disponible")
    void testCotizarSinTotal() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/cotizaciones"))
                .andRespond(withSuccess("{\"servicioId\":1,\"zona\":\"MISMA_REGION\"}", MediaType.APPLICATION_JSON));

        assertThrows(CatalogoNoDisponibleException.class,
                () -> catalogoClient.cotizar(TestEnvioFactory.solicitudCotizacion()));
    }

    @Test
    @DisplayName("Descontar capacidad: un 500 del catalogo es CatalogoNoDisponibleException (503), no IllegalStateException")
    void testDescontarCapacidadErrorServidor() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/servicios/4/descontar-capacidad"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(CatalogoNoDisponibleException.class, () -> catalogoClient.descontarCapacidad(4L));
    }

    @Test
    @DisplayName("Restablecer capacidad: usa PATCH /servicios/{id}/restablecer-capacidad con cabecera interna")
    void testRestablecerCapacidad() {
        mockServer.expect(requestTo("http://localhost:8082/api/catalogo/servicios/5/restablecer-capacidad"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Servicio-Interno", SECRETO_TEST))
                .andRespond(withSuccess("{\"id\":5}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> catalogoClient.restablecerCapacidad(5L));

        mockServer.verify();
    }
}
