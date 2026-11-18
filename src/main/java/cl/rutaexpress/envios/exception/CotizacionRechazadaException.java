package cl.rutaexpress.envios.exception;

/**
 * El catalogo rechazo la cotizacion por una regla de negocio o por parametros invalidos.
 * El status (400 o 422) es el que se devuelve al cliente.
 */
public class CotizacionRechazadaException extends RuntimeException {

    private final int status;
    private final String codigo;

    public CotizacionRechazadaException(int status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
