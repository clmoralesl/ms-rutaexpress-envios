package cl.rutaexpress.envios.exception;

public class EnvioSinPrecioFinalException extends RuntimeException {

    public static final String CODIGO = "ENVIO_SIN_PRECIO_FINAL";

    public EnvioSinPrecioFinalException(Long envioId) {
        super("El envío " + envioId + " no tiene precio final; debe aceptarse con POST /api/envios/{id}/aceptacion");
    }
}
