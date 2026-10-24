package cl.rutaexpress.envios.dto;

import java.util.List;

public record RegionConComunasResponse(
        Integer idRegion,
        String nombre,
        List<ComunaItemResponse> comunas
) {
    public record ComunaItemResponse(
            Integer idComuna,
            String nombre
    ) {
    }
}
