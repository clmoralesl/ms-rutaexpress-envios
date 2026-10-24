package cl.rutaexpress.envios.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "COMUNAS", schema = "USER_ENVIOS")
@Immutable
public class Comuna {

    @Id
    @Column(name = "id_comuna")
    private Integer idComuna;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_region", nullable = false)
    private Region region;

    protected Comuna() {
    }

    public Comuna(Integer idComuna, String nombre, Region region) {
        this.idComuna = idComuna;
        this.nombre = nombre;
        this.region = region;
    }

    public Integer getIdComuna() {
        return idComuna;
    }

    public String getNombre() {
        return nombre;
    }

    public Region getRegion() {
        return region;
    }
}
