package cl.rutaexpress.envios.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, String>> manejarAccesoDenegado(
            AccesoDenegadoException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> manejarEnvioNoEncontrado(
            EnvioNoEncontradoException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(TransicionEstadoInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejarTransicionInvalida(
            TransicionEstadoInvalidaException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(CapacidadFlotaAgotadaException.class)
    public ResponseEntity<Map<String, String>> manejarCapacidadFlotaAgotada(
            CapacidadFlotaAgotadaException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(ComunaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> manejarComunaNoEncontrada(
            ComunaNoEncontradaException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(DireccionInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejarDireccionInvalida(
            DireccionInvalidaException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejarCatalogoNoDisponible(
            CatalogoNoDisponibleException ex) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "mensaje", "El servicio de catálogo no está disponible; intente nuevamente en unos instantes",
                        "codigo", "CATALOGO_NO_DISPONIBLE"));
    }

    @ExceptionHandler(CotizacionRechazadaException.class)
    public ResponseEntity<Map<String, String>> manejarCotizacionRechazada(
            CotizacionRechazadaException ex) {

        HttpStatus estado = ex.getStatus() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity
                .status(estado)
                .body(Map.of(
                        "mensaje", ex.getMessage() != null ? ex.getMessage() : "Cotización rechazada por el catálogo",
                        "codigo", ex.getCodigo() != null ? ex.getCodigo() : "COTIZACION_RECHAZADA"));
    }

    @ExceptionHandler(EnvioSinServicioException.class)
    public ResponseEntity<Map<String, String>> manejarEnvioSinServicio(
            EnvioSinServicioException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage(), "codigo", EnvioSinServicioException.CODIGO));
    }

    @ExceptionHandler(EnvioSinPrecioFinalException.class)
    public ResponseEntity<Map<String, String>> manejarEnvioSinPrecioFinal(
            EnvioSinPrecioFinalException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage(), "codigo", EnvioSinPrecioFinalException.CODIGO));
    }

    @ExceptionHandler(EnvioBloqueadoException.class)
    public ResponseEntity<Map<String, String>> manejarEnvioBloqueado(
            EnvioBloqueadoException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage(), "codigo", "ENVIO_BLOQUEADO"));
    }

    @ExceptionHandler(AceptacionRequiereEndpointException.class)
    public ResponseEntity<Map<String, String>> manejarAceptacionRequiereEndpoint(
            AceptacionRequiereEndpointException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(CorreoInvalidoException.class)
    public ResponseEntity<Map<String, String>> manejarCorreoInvalido(
            CorreoInvalidoException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidaciones(
            MethodArgumentNotValidException ex) {

        Map<String, String> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Campo no válido",
                        (v1, v2) -> v1
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "mensaje", "Error de validación en la solicitud",
                        "errores", errores
                ));
    }
}
