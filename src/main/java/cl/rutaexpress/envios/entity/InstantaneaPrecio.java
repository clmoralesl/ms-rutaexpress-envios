package cl.rutaexpress.envios.entity;

import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoResponse;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Copia de los componentes de una cotizacion del catalogo en el momento de calcularla.
 * Se usa dos veces en Envio: precio estimado (pre-registro) y precio final (aceptacion).
 */
@Embeddable
public record InstantaneaPrecio(
        String zona,
        BigDecimal factorZona,
        BigDecimal tarifaBase,
        BigDecimal precioPorKg,
        BigDecimal pesoFacturableKg,
        Long totalClp
) {

    public static InstantaneaPrecio desde(CotizacionCatalogoResponse cotizacion) {
        return new InstantaneaPrecio(
                cotizacion.zona(),
                cotizacion.factorZona(),
                cotizacion.tarifaBase(),
                cotizacion.precioPorKg(),
                cotizacion.pesoFacturableKg(),
                cotizacion.totalClp()
        );
    }
}
