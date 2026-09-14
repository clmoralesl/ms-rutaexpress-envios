package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoRequest(

        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoEnvio estado
) {
}