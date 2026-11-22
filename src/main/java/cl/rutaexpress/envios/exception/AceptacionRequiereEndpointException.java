package cl.rutaexpress.envios.exception;

public class AceptacionRequiereEndpointException extends RuntimeException {

    public AceptacionRequiereEndpointException() {
        super("La aceptacion se registra con POST /api/envios/{id}/aceptacion");
    }
}
