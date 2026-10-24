package cl.rutaexpress.envios.exception;

public class ComunaNoEncontradaException extends RuntimeException {

    private final Integer idComuna;

    public ComunaNoEncontradaException(Integer idComuna) {
        super("La comuna con código CUT " + idComuna + " no existe en el catálogo geográfico");
        this.idComuna = idComuna;
    }

    public Integer getIdComuna() {
        return idComuna;
    }
}
