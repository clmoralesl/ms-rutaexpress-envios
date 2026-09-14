package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Envio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnvioResponse(
        Long id,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        String direccionOrigen,
        String direccionDestino,
        BigDecimal pesoKg,
        EstadoEnvio estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {

    public static EnvioResponse fromEntity(Envio envio) {
        return new EnvioResponse(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                envio.getRutRemitente(),
                envio.getRutDestinatario(),
                envio.getDireccionOrigen(),
                envio.getDireccionDestino(),
                envio.getPesoKg(),
                envio.getEstado(),
                envio.getFechaCreacion(),
                envio.getFechaActualizacion()
        );
    }
}
