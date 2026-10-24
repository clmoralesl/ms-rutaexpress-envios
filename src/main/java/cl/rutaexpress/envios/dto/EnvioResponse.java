package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnvioResponse(
        Long id,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        DireccionResponse origen,
        DireccionResponse destino,
        String direccionOrigen,
        String direccionDestino,
        BigDecimal pesoKg,
        EstadoEnvio estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {

    public EnvioResponse(
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
        this(
                id,
                codigoSeguimiento,
                rutRemitente,
                rutDestinatario,
                null,
                null,
                direccionOrigen,
                direccionDestino,
                pesoKg,
                estado,
                fechaCreacion,
                fechaActualizacion
        );
    }

    public static EnvioResponse fromEntity(Envio envio, String direccionOrigenCompuesta, String direccionDestinoCompuesta) {
        DireccionResponse origenDto = new DireccionResponse(
                envio.getCalleOrigen(),
                envio.getNumeroOrigen(),
                envio.getComplementoOrigen(),
                envio.getComunaOrigen() != null ? envio.getComunaOrigen().getIdComuna() : null,
                envio.getComunaOrigen() != null ? envio.getComunaOrigen().getNombre() : null,
                (envio.getComunaOrigen() != null && envio.getComunaOrigen().getRegion() != null)
                        ? envio.getComunaOrigen().getRegion().getNombre()
                        : null,
                direccionOrigenCompuesta
        );

        DireccionResponse destinoDto = new DireccionResponse(
                envio.getCalleDestino(),
                envio.getNumeroDestino(),
                envio.getComplementoDestino(),
                envio.getComunaDestino() != null ? envio.getComunaDestino().getIdComuna() : null,
                envio.getComunaDestino() != null ? envio.getComunaDestino().getNombre() : null,
                (envio.getComunaDestino() != null && envio.getComunaDestino().getRegion() != null)
                        ? envio.getComunaDestino().getRegion().getNombre()
                        : null,
                direccionDestinoCompuesta
        );

        return new EnvioResponse(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                envio.getRutRemitente(),
                envio.getRutDestinatario(),
                origenDto,
                destinoDto,
                direccionOrigenCompuesta,
                direccionDestinoCompuesta,
                envio.getPesoKg(),
                envio.getEstado(),
                envio.getFechaCreacion(),
                envio.getFechaActualizacion()
        );
    }
}
