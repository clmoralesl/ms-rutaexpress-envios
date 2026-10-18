package cl.rutaexpress.envios.dto;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record EventoEnvioKafka(
        UUID eventoId,
        Long envioId,
        String codigoSeguimiento,
        EstadoEnvio estadoAnterior,
        EstadoEnvio estadoNuevo,
        String usuarioEmail,
        String usuarioRol,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,
        Map<String, Object> detalles
) {

    public static final String USUARIO_POR_DEFECTO_EMAIL = "sistema@rutaexpress.cl";
    public static final String USUARIO_POR_DEFECTO_ROL = "SISTEMA";

    public static EventoEnvioKafka deTransicion(
            Envio envio,
            EstadoEnvio estadoAnterior,
            EstadoEnvio estadoNuevo,
            String usuarioEmail,
            String usuarioRol,
            Map<String, Object> detallesAdicionales) {

        UUID id = UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        String email = (usuarioEmail != null && !usuarioEmail.isBlank()) ? usuarioEmail : USUARIO_POR_DEFECTO_EMAIL;
        String rol = (usuarioRol != null && !usuarioRol.isBlank()) ? usuarioRol : USUARIO_POR_DEFECTO_ROL;

        Map<String, Object> mapDetalles = new LinkedHashMap<>();
        if (envio != null) {
            mapDetalles.put("pesoKg", envio.getPesoKg());
            mapDetalles.put("direccionOrigen", envio.getDireccionOrigen());
            mapDetalles.put("direccionDestino", envio.getDireccionDestino());
            mapDetalles.put("rutRemitente", envio.getRutRemitente());
            mapDetalles.put("rutDestinatario", envio.getRutDestinatario());
        }
        if (detallesAdicionales != null) {
            mapDetalles.putAll(detallesAdicionales);
        }

        return new EventoEnvioKafka(
                id,
                envio != null ? envio.getId() : null,
                envio != null ? envio.getCodigoSeguimiento() : null,
                estadoAnterior,
                estadoNuevo,
                email,
                rol,
                ahora,
                mapDetalles
        );
    }
}
