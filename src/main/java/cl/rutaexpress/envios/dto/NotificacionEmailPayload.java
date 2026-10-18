package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;

import java.time.LocalDateTime;

public record NotificacionEmailPayload(
        Long envioId,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        EstadoEnvio estado,
        String asunto,
        String mensaje,
        LocalDateTime fecha
) {
}
