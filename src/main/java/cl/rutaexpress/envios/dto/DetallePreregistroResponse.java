package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.InstantaneaPrecio;
import cl.rutaexpress.envios.entity.Medidas;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos de pre-registro y aceptacion de un envio. Es null en la respuesta de los
 * envios antiguos que no tienen servicio ni sucursal.
 */
public record DetallePreregistroResponse(
        Long sucursalOrigenId,
        Long servicioId,
        Medidas medidasDeclaradas,
        InstantaneaPrecio precioEstimado,
        BigDecimal pesoVerificadoKg,
        Medidas medidasVerificadas,
        InstantaneaPrecio precioFinal,
        LocalDateTime fechaAceptacion
) {

    public static DetallePreregistroResponse desde(Envio envio) {
        if (envio.getServicioId() == null && envio.getSucursalOrigenId() == null) {
            return null;
        }
        return new DetallePreregistroResponse(
                envio.getSucursalOrigenId(),
                envio.getServicioId(),
                envio.getMedidasDeclaradas(),
                envio.getPrecioEstimado(),
                envio.getPesoVerificadoKg(),
                envio.getMedidasVerificadas(),
                envio.getPrecioFinal(),
                envio.getFechaAceptacion()
        );
    }
}
