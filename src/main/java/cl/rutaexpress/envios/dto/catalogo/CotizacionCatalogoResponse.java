package cl.rutaexpress.envios.dto.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Replica los campos usados de CotizacionResponse de ms-rutaexpress-catalogo.
 * Los campos desconocidos se ignoran para tolerar cambios aditivos del catalogo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CotizacionCatalogoResponse(
        Long servicioId,
        Long sucursalOrigenId,
        Integer idComunaOrigen,
        String zona,
        BigDecimal factorZona,
        BigDecimal tarifaBase,
        BigDecimal precioPorKg,
        BigDecimal pesoFacturableKg,
        Long totalClp,
        DireccionSucursal direccionSucursal
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DireccionSucursal(
            String nombre,
            String calle,
            String numero,
            String complemento,
            Integer idComuna
    ) {
    }
}
