package cl.rutaexpress.envios.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MensajeEnvelope<T>(
        String tipo,
        String eventoId,
        LocalDateTime timestamp,
        String traceId,
        String correlationId,
        T datos
) {

    public static <T> MensajeEnvelope<T> de(String tipo, T datos) {
        String id = UUID.randomUUID().toString();
        String trace = UUID.randomUUID().toString();
        return new MensajeEnvelope<>(tipo, id, LocalDateTime.now(), trace, id, datos);
    }

    public static <T> MensajeEnvelope<T> de(String tipo, String correlationId, T datos) {
        String id = UUID.randomUUID().toString();
        String trace = UUID.randomUUID().toString();
        return new MensajeEnvelope<>(
                tipo,
                id,
                LocalDateTime.now(),
                trace,
                correlationId != null ? correlationId : id,
                datos
        );
    }

    public static <T> MensajeEnvelope<T> de(String tipo, String traceId, String correlationId, T datos) {
        String id = UUID.randomUUID().toString();
        return new MensajeEnvelope<>(
                tipo,
                id,
                LocalDateTime.now(),
                traceId != null ? traceId : UUID.randomUUID().toString(),
                correlationId != null ? correlationId : id,
                datos
        );
    }
}
