package cl.rutaexpress.envios.entity;

import jakarta.persistence.Embeddable;

/**
 * Medidas de un paquete en centimetros. Se usa dos veces en Envio: declaradas al
 * pre-registrar y verificadas al aceptar.
 */
@Embeddable
public record Medidas(Integer largoCm, Integer anchoCm, Integer altoCm) {

    /**
     * Devuelve las medidas si las tres vienen informadas; null si no viene ninguna.
     * Los casos parciales se rechazan antes, en la validacion del DTO.
     */
    public static Medidas de(Integer largoCm, Integer anchoCm, Integer altoCm) {
        if (largoCm == null && anchoCm == null && altoCm == null) {
            return null;
        }
        return new Medidas(largoCm, anchoCm, altoCm);
    }
}
