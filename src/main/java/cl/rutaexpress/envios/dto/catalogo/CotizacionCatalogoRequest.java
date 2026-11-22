package cl.rutaexpress.envios.dto.catalogo;

import cl.rutaexpress.envios.entity.Medidas;

import java.math.BigDecimal;

/**
 * Cuerpo de POST /api/catalogo/cotizaciones. Los nombres JSON coinciden con
 * CotizacionRequest de ms-rutaexpress-catalogo.
 */
public record CotizacionCatalogoRequest(
        Long servicioId,
        Long sucursalOrigenId,
        Integer idComunaDestino,
        BigDecimal pesoKg,
        Integer largoCm,
        Integer anchoCm,
        Integer altoCm,
        Boolean validarSucursalActiva
) {

    public CotizacionCatalogoRequest(
            Long servicioId,
            Long sucursalOrigenId,
            Integer idComunaDestino,
            BigDecimal pesoKg,
            Medidas medidas,
            Boolean validarSucursalActiva) {
        this(servicioId, sucursalOrigenId, idComunaDestino, pesoKg,
                medidas != null ? medidas.largoCm() : null,
                medidas != null ? medidas.anchoCm() : null,
                medidas != null ? medidas.altoCm() : null,
                validarSucursalActiva);
    }
}
