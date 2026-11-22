package cl.rutaexpress.envios.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AceptarEnvioRequest(

        @NotNull(message = "El peso verificado es obligatorio")
        @DecimalMin(value = "0.01", message = "El peso verificado debe ser mayor que cero")
        @DecimalMax(value = "500.00", message = "El peso verificado no puede superar 500 kg")
        BigDecimal pesoVerificadoKg,

        @Min(value = 1, message = "El largo debe ser al menos 1 cm")
        @Max(value = 300, message = "El largo no puede superar 300 cm")
        Integer largoCm,

        @Min(value = 1, message = "El ancho debe ser al menos 1 cm")
        @Max(value = 300, message = "El ancho no puede superar 300 cm")
        Integer anchoCm,

        @Min(value = 1, message = "El alto debe ser al menos 1 cm")
        @Max(value = 300, message = "El alto no puede superar 300 cm")
        Integer altoCm,

        @Size(max = 500, message = "La observacion no puede superar 500 caracteres")
        String observacion
) {

    @AssertTrue(message = "Las medidas deben informarse completas (largo, ancho y alto) o no informarse")
    @JsonIgnore
    public boolean isMedidasConsistentes() {
        int informadas = (largoCm != null ? 1 : 0) + (anchoCm != null ? 1 : 0) + (altoCm != null ? 1 : 0);
        return informadas == 0 || informadas == 3;
    }
}
