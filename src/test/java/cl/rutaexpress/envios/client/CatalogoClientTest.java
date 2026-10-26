package cl.rutaexpress.envios.client;

import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
