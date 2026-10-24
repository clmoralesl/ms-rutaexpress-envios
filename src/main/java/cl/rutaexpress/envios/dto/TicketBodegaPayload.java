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
        String instrucciones,
        String calleOrigen,
        String numeroOrigen,
        String complementoOrigen,
        String comunaOrigen,
        String regionOrigen,
        String calleDestino,
        String numeroDestino,
        String complementoDestino,
        String comunaDestino,
        String regionDestino
) {
    public TicketBodegaPayload(
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
        this(
                envioId,
                codigoSeguimiento,
                rutRemitente,
                rutDestinatario,
                direccionOrigen,
                direccionDestino,
                pesoKg,
                fechaEmision,
                instrucciones,
                null, null, null, null, null,
                null, null, null, null, null
        );
    }
}
