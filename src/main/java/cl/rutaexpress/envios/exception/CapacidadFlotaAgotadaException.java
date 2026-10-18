package cl.rutaexpress.envios.exception;

public class CapacidadFlotaAgotadaException extends RuntimeException {

    public CapacidadFlotaAgotadaException(String mensaje) {
        super(mensaje);
    }

    public CapacidadFlotaAgotadaException(Long servicioId) {
        super("Capacidad de flota agotada para el servicio ID " + servicioId);
    }
}
