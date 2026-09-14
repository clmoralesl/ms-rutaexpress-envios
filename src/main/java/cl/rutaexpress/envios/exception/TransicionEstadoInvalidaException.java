package cl.rutaexpress.envios.exception;

import cl.rutaexpress.envios.entity.EstadoEnvio;

public class TransicionEstadoInvalidaException extends RuntimeException {

    public TransicionEstadoInvalidaException(
            EstadoEnvio estadoActual,
            EstadoEnvio nuevoEstado) {

        super(
                "Transición de estado inválida: "
                        + estadoActual
                        + " -> "
                        + nuevoEstado
        );
    }
}