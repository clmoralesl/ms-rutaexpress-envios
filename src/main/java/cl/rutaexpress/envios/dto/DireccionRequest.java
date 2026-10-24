package cl.rutaexpress.envios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DireccionRequest(

        @NotBlank(message = "El nombre de la calle es obligatorio")
        @Size(max = 120, message = "La calle no puede exceder 120 caracteres")
        String calle,

        @NotBlank(message = "El número municipal es obligatorio (use S/N si no posee número)")
        @Size(max = 20, message = "El número no puede exceder 20 caracteres")
        String numero,

        @Size(max = 80, message = "El complemento no puede exceder 80 caracteres")
        String complemento,

        @NotNull(message = "El identificador de comuna (código CUT) es obligatorio")
        Integer idComuna
) {
}
