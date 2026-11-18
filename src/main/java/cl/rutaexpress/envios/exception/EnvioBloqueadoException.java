package cl.rutaexpress.envios.exception;

public class EnvioBloqueadoException extends RuntimeException {

    public EnvioBloqueadoException(Long envioId) {
        super("El envío " + envioId + " está siendo modificado por otra operación; reintente en unos segundos");
    }
}
