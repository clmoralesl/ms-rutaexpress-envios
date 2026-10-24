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
        String dirOrigen = componerDireccion(envio, true);
        String dirDestino = componerDireccion(envio, false);
        return deTransicion(envio, estadoAnterior, estadoNuevo, usuarioEmail, usuarioRol, dirOrigen, dirDestino, detallesAdicionales);
    }

    private static String componerDireccion(Envio envio, boolean esOrigen) {
        if (envio == null) return null;
        String calle = esOrigen ? envio.getCalleOrigen() : envio.getCalleDestino();
        String numero = esOrigen ? envio.getNumeroOrigen() : envio.getNumeroDestino();
        String complemento = esOrigen ? envio.getComplementoOrigen() : envio.getComplementoDestino();
        var comuna = esOrigen ? envio.getComunaOrigen() : envio.getComunaDestino();

        if (calle == null && comuna == null) return null;

        String nombreComuna = comuna != null ? comuna.getNombre() : null;
        String nombreRegion = (comuna != null && comuna.getRegion() != null) ? comuna.getRegion().getNombre() : null;

        return cl.rutaexpress.envios.service.NormalizadorDireccion.componer(
                calle, numero, complemento, nombreComuna, nombreRegion
        );
    }

    public static EventoEnvioKafka deTransicion(
            Envio envio,
            EstadoEnvio estadoAnterior,
            EstadoEnvio estadoNuevo,
            String usuarioEmail,
            String usuarioRol,
            String direccionOrigenCompuesta,
            String direccionDestinoCompuesta,
            Map<String, Object> detallesAdicionales) {

        UUID id = UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        String email = (usuarioEmail != null && !usuarioEmail.isBlank()) ? usuarioEmail : USUARIO_POR_DEFECTO_EMAIL;
        String rol = (usuarioRol != null && !usuarioRol.isBlank()) ? usuarioRol : USUARIO_POR_DEFECTO_ROL;

        Map<String, Object> mapDetalles = new LinkedHashMap<>();
        if (envio != null) {
            mapDetalles.put("pesoKg", envio.getPesoKg());
            mapDetalles.put("rutRemitente", envio.getRutRemitente());
            mapDetalles.put("rutDestinatario", envio.getRutDestinatario());

            // Direcciones compuestas (compatibilidad legacy)
            mapDetalles.put("direccionOrigen", direccionOrigenCompuesta);
            mapDetalles.put("direccionDestino", direccionDestinoCompuesta);

            // Estructura normalizada de origen
            mapDetalles.put("calleOrigen", envio.getCalleOrigen());
            mapDetalles.put("numeroOrigen", envio.getNumeroOrigen());
            mapDetalles.put("complementoOrigen", envio.getComplementoOrigen());
            if (envio.getComunaOrigen() != null) {
                mapDetalles.put("idComunaOrigen", envio.getComunaOrigen().getIdComuna());
                mapDetalles.put("comunaOrigen", envio.getComunaOrigen().getNombre());
                if (envio.getComunaOrigen().getRegion() != null) {
                    mapDetalles.put("regionOrigen", envio.getComunaOrigen().getRegion().getNombre());
                }
            }

            // Estructura normalizada de destino
            mapDetalles.put("calleDestino", envio.getCalleDestino());
            mapDetalles.put("numeroDestino", envio.getNumeroDestino());
            mapDetalles.put("complementoDestino", envio.getComplementoDestino());
            if (envio.getComunaDestino() != null) {
                mapDetalles.put("idComunaDestino", envio.getComunaDestino().getIdComuna());
                mapDetalles.put("comunaDestino", envio.getComunaDestino().getNombre());
                if (envio.getComunaDestino().getRegion() != null) {
                    mapDetalles.put("regionDestino", envio.getComunaDestino().getRegion().getNombre());
                }
            }
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
