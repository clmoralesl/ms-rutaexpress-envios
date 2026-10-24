package cl.rutaexpress.envios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CrearEnvioRequest(

        // Campo opcional: el servidor ignora cualquier valor enviado y genera el código institucional de forma autónoma
        String codigoSeguimiento,

        @NotBlank(message = "El RUT del remitente es obligatorio")
        String rutRemitente,

        @NotBlank(message = "El RUT del destinatario es obligatorio")
        String rutDestinatario,

        @NotNull(message = "La dirección de origen estructurada es obligatoria")
        @Valid
        DireccionRequest origen,

        @NotNull(message = "La dirección de destino estructurada es obligatoria")
        @Valid
        DireccionRequest destino,

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que cero")
        BigDecimal pesoKg
) {
}
