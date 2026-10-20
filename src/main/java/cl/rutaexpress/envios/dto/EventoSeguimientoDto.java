package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;

import java.time.LocalDateTime;

public record EventoSeguimientoDto(
        EstadoEnvio estado,
        String descripcion,
        LocalDateTime fecha
) {
}
