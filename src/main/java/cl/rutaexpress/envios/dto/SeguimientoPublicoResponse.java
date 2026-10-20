package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SeguimientoPublicoResponse(
        String codigoSeguimiento,
        EstadoEnvio estadoActual,
        String ciudadOrigen,
        String ciudadDestino,
        LocalDateTime fechaEstimadaEntrega,
        String servicioContratado,
        String courierAsignado,
        BigDecimal pesoKg,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        List<EventoSeguimientoDto> historial
) {
}
