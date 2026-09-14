package cl.rutaexpress.envios.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CrearEnvioRequest(

        @NotBlank(message = "El código de seguimiento es obligatorio")
        String codigoSeguimiento,

        @NotBlank(message = "El RUT del remitente es obligatorio")
        String rutRemitente,

        @NotBlank(message = "El RUT del destinatario es obligatorio")
        String rutDestinatario,

        @NotBlank(message = "La dirección de origen es obligatoria")
        String direccionOrigen,

        @NotBlank(message = "La dirección de destino es obligatoria")
        String direccionDestino,

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que cero")
        BigDecimal pesoKg
) {
}
