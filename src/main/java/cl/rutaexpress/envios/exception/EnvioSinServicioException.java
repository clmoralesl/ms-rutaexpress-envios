package cl.rutaexpress.envios.exception;

public class EnvioSinServicioException extends RuntimeException {

    public static final String CODIGO = "ENVIO_SIN_SERVICIO";

    public EnvioSinServicioException(Long envioId) {
        super("El envío " + envioId + " no tiene servicio ni sucursal de origen; no puede aceptarse");
    }
}
