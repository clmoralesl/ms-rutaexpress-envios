package cl.rutaexpress.envios.exception;

public class EnvioNoEncontradoException extends RuntimeException {

    public EnvioNoEncontradoException(Long id) {
        super("No existe un envío con ID " + id);
    }

    public EnvioNoEncontradoException(String codigoSeguimiento) {
        super("No existe un envío con código de seguimiento " + codigoSeguimiento);
    }
}
