package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Envio;

import java.time.LocalDateTime;

public record SeguimientoPublicoResponse(
        String codigoSeguimiento,
        EstadoEnvio estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
    public static SeguimientoPublicoResponse fromEntity(Envio envio) {
        return new SeguimientoPublicoResponse(
                envio.getCodigoSeguimiento(),
                envio.getEstado(),
                envio.getFechaCreacion(),
                envio.getFechaActualizacion()
        );
    }
}
