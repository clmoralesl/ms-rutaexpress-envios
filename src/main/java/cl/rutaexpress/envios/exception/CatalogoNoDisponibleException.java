package cl.rutaexpress.envios.exception;

public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
