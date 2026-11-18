package cl.rutaexpress.envios.exception;

public class CorreoInvalidoException extends RuntimeException {

    public CorreoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
