package cl.rutaexpress.envios.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CrearEnvioRequest(

        // Campo opcional: el servidor ignora cualquier valor enviado y genera el código institucional de forma autónoma
        String codigoSeguimiento,

        @NotBlank(message = "El RUT del remitente es obligatorio")
        String rutRemitente,

        @NotBlank(message = "El RUT del destinatario es obligatorio")
        String rutDestinatario,

        @NotNull(message = "La sucursal de origen es obligatoria")
        @Positive(message = "La sucursal de origen debe ser un identificador positivo")
        Long sucursalOrigenId,

        @NotNull(message = "El servicio es obligatorio")
        @Positive(message = "El servicio debe ser un identificador positivo")
        Long servicioId,

        @NotNull(message = "La dirección de destino estructurada es obligatoria")
        @Valid
        DireccionRequest destino,

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que cero")
        @DecimalMax(value = "500.00", message = "El peso no puede superar 500 kg")
        BigDecimal pesoKg,

        @Min(value = 1, message = "El largo debe ser al menos 1 cm")
        @Max(value = 300, message = "El largo no puede superar 300 cm")
        Integer largoCm,

        @Min(value = 1, message = "El ancho debe ser al menos 1 cm")
        @Max(value = 300, message = "El ancho no puede superar 300 cm")
        Integer anchoCm,

        @Min(value = 1, message = "El alto debe ser al menos 1 cm")
        @Max(value = 300, message = "El alto no puede superar 300 cm")
        Integer altoCm,

        // Solo se considera cuando el usuario puede gestionar (ADMIN o DESPACHADOR); para CLIENTE se usa el correo del token
        String remitenteEmail
) {

    @AssertTrue(message = "Las medidas deben informarse completas (largo, ancho y alto) o no informarse")
    @JsonIgnore
    public boolean isMedidasConsistentes() {
        int informadas = (largoCm != null ? 1 : 0) + (anchoCm != null ? 1 : 0) + (altoCm != null ? 1 : 0);
        return informadas == 0 || informadas == 3;
    }
}
