package cl.rutaexpress.envios.client;

import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoRequest;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoResponse;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
import cl.rutaexpress.envios.exception.CotizacionRechazadaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    @Autowired
    public CatalogoClient(
            @Value("${catalogo.service.url:http://localhost:8082}") String catalogoUrl,
            @Value("${rutaexpress.secreto.interno}") String secretoInterno,
            @Value("${catalogo.timeout.conexion-ms:2000}") int timeoutConexionMs,
            @Value("${catalogo.timeout.lectura-ms:3000}") int timeoutLecturaMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutConexionMs))
                .build();
        // JdkClientHttpRequestFactory admite PATCH; HttpURLConnection (SimpleClientHttpRequestFactory) no.
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutLecturaMs));

        this.restClient = RestClient.builder()
                .baseUrl(catalogoUrl)
                .requestFactory(fabrica)
                .defaultHeader("X-Servicio-Interno", secretoInterno)
                .build();
    }

    public CatalogoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public CotizacionCatalogoResponse cotizar(CotizacionCatalogoRequest solicitud) {
        log.info("Solicitando cotizacion a ms-rutaexpress-catalogo (servicio {}, sucursal {})",
                solicitud.servicioId(), solicitud.sucursalOrigenId());
        CotizacionCatalogoResponse respuesta;
        try {
            respuesta = restClient.post()
                    .uri("/api/catalogo/cotizaciones")
                    .body(solicitud)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int status = response.getStatusCode().value();
                        String cuerpo = leerCuerpo(response.getBody());
                        if (status == 400) {
                            throw rechazo(400, cuerpo);
                        }
                        if (status == 404 || status == 422) {
                            throw rechazo(422, cuerpo);
                        }
                        log.error("Error del catalogo al cotizar: HTTP {}", status);
                        throw new CatalogoNoDisponibleException("Catalogo respondio HTTP " + status);
                    })
                    .body(CotizacionCatalogoResponse.class);
        } catch (ResourceAccessException e) {
            log.error("Catalogo inaccesible al cotizar: {}", e.getMessage());
            throw new CatalogoNoDisponibleException("Catalogo inaccesible o con tiempo de espera agotado", e);
        }
        if (respuesta == null || respuesta.totalClp() == null) {
            throw new CatalogoNoDisponibleException("Catalogo devolvio una cotizacion vacia o sin total");
        }
        return respuesta;
    }

    public void descontarCapacidad(Long servicioId) {
        log.info("Invocando ms-rutaexpress-catalogo para descontar capacidad del servicio ID {}", servicioId);

        try {
            restClient.patch()
                    .uri("/api/catalogo/servicios/{id}/descontar-capacidad", servicioId)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (request, response) -> {
                        log.warn("Capacidad de flota agotada al descontar cupo para el servicio ID {}", servicioId);
                        throw new CapacidadFlotaAgotadaException(servicioId);
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String mensajeError = "Error al comunicarse con ms-rutaexpress-catalogo: HTTP " + response.getStatusCode();
                        log.error(mensajeError);
                        throw new CatalogoNoDisponibleException(mensajeError);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            log.error("Catalogo inaccesible al descontar capacidad del servicio {}: {}", servicioId, e.getMessage());
            throw new CatalogoNoDisponibleException("Catalogo inaccesible o con tiempo de espera agotado", e);
        }

        log.info("Capacidad descontada exitosamente en catálogo para servicio ID {}", servicioId);
    }

    public void restablecerCapacidad(Long servicioId) {
        log.info("Invocando ms-rutaexpress-catalogo para restablecer capacidad del servicio ID {}", servicioId);

        try {
            restClient.patch()
                    .uri("/api/catalogo/servicios/{id}/restablecer-capacidad", servicioId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String mensajeError = "Error al restablecer capacidad en ms-rutaexpress-catalogo: HTTP " + response.getStatusCode();
                        log.error(mensajeError);
                        throw new CatalogoNoDisponibleException(mensajeError);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            log.error("Catalogo inaccesible al restablecer capacidad del servicio {}: {}", servicioId, e.getMessage());
            throw new CatalogoNoDisponibleException("Catalogo inaccesible o con tiempo de espera agotado", e);
        }
    }

    private static String leerCuerpo(java.io.InputStream flujo) {
        try {
            return new String(flujo.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static CotizacionRechazadaException rechazo(int status, String cuerpo) {
        String codigo = null;
        String mensaje = null;
        try {
            JsonNode json = MAPPER.readTree(cuerpo);
            if (json != null && json.isObject()) {
                if (json.hasNonNull("codigo")) {
                    codigo = json.get("codigo").asText();
                } else if (json.hasNonNull("error")) {
                    codigo = json.get("error").asText();
                }
                if (json.hasNonNull("mensaje")) {
                    mensaje = json.get("mensaje").asText();
                }
            }
        } catch (IOException | RuntimeException e) {
            // cuerpo no JSON: se usan los textos genericos
        }
        if (codigo == null) {
            codigo = "COTIZACION_RECHAZADA";
        }
        if (mensaje == null) {
            mensaje = "El catálogo rechazó la cotización";
        }
        return new CotizacionRechazadaException(status, codigo, mensaje);
    }
}
