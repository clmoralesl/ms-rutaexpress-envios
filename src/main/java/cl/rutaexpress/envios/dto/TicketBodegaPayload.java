package cl.rutaexpress.envios.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketBodegaPayload(
        Long envioId,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        String direccionOrigen,
        String direccionDestino,
        BigDecimal pesoKg,
        LocalDateTime fechaEmision,
        String instrucciones
) {
}
