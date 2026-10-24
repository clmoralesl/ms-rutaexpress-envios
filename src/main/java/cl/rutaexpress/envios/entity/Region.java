package cl.rutaexpress.envios.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "REGIONES", schema = "USER_ENVIOS")
@Immutable
public class Region {

    @Id
    @Column(name = "id_region")
    private Integer idRegion;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @OneToMany(mappedBy = "region", fetch = FetchType.LAZY)
    private List<Comuna> comunas = new ArrayList<>();

    protected Region() {
    }

    public Region(Integer idRegion, String nombre) {
        this.idRegion = idRegion;
        this.nombre = nombre;
    }

    public Integer getIdRegion() {
        return idRegion;
    }

    public String getNombre() {
        return nombre;
    }

    public List<Comuna> getComunas() {
        return comunas;
    }
}
