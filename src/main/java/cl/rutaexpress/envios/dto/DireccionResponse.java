package cl.rutaexpress.envios.dto;

public record DireccionResponse(
        String calle,
        String numero,
        String complemento,
        Integer idComuna,
        String comuna,
        String region,
        String direccionCompleta
) {
}
