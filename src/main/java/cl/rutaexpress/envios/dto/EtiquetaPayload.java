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
        LocalDateTime fechaSolicitud
) {
}
