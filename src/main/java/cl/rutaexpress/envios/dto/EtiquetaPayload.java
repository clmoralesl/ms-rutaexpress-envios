package cl.rutaexpress.envios.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EtiquetaPayload(
        Long envioId,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        String direccionOrigen,
        String direccionDestino,
        BigDecimal pesoKg,
        String formatoEtiqueta,
        LocalDateTime fechaSolicitud,
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
    public EtiquetaPayload(
            Long envioId,
            String codigoSeguimiento,
            String rutRemitente,
            String rutDestinatario,
            String direccionOrigen,
            String direccionDestino,
            BigDecimal pesoKg,
            String formatoEtiqueta,
            LocalDateTime fechaSolicitud
    ) {
        this(
                envioId,
                codigoSeguimiento,
                rutRemitente,
                rutDestinatario,
                direccionOrigen,
                direccionDestino,
                pesoKg,
                formatoEtiqueta,
                fechaSolicitud,
                null, null, null, null, null,
                null, null, null, null, null
        );
    }
}
