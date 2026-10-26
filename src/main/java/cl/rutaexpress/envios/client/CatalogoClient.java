package cl.rutaexpress.envios.client;

import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

    private final RestClient restClient;

    @Autowired
    public CatalogoClient(
            @Value("${catalogo.service.url:http://localhost:8082}") String catalogoUrl,
            @Value("${rutaexpress.secreto.interno}") String secretoInterno) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogoUrl)
                .defaultHeader("X-Servicio-Interno", secretoInterno)
                .build();
    }

    public CatalogoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void descontarCapacidad(Long servicioId) {
        log.info("Invocando ms-rutaexpress-catalogo para descontar capacidad del servicio ID {}", servicioId);

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
                    throw new IllegalStateException(mensajeError);
                })
                .toBodilessEntity();

        log.info("Capacidad descontada exitosamente en catálogo para servicio ID {}", servicioId);
    }
}
