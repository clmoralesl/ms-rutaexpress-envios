package cl.rutaexpress.envios.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ENVIOS", schema = "USER_ENVIOS")
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo_seguimiento", nullable = false, unique = true, length = 50)
    private String codigoSeguimiento;

    @Column(name = "rut_remitente", nullable = false, length = 20)
    private String rutRemitente;

    @Column(name = "rut_destinatario", nullable = false, length = 20)
    private String rutDestinatario;

    @Column(name = "direccion_origen", nullable = false, length = 255)
    private String direccionOrigen;

    @Column(name = "direccion_destino", nullable = false, length = 255)
    private String direccionDestino;

    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pesoKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoEnvio estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Envio() {
    }

    public Envio(
            String codigoSeguimiento,
            String rutRemitente,
            String rutDestinatario,
            String direccionOrigen,
            String direccionDestino,
            BigDecimal pesoKg,
            EstadoEnvio estado
    ) {
        this.codigoSeguimiento = codigoSeguimiento;
        this.rutRemitente = rutRemitente;
        this.rutDestinatario = rutDestinatario;
        this.direccionOrigen = direccionOrigen;
        this.direccionDestino = direccionDestino;
        this.pesoKg = pesoKg;
        this.estado = estado;
    }

    @PrePersist
    protected void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();

        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }

        fechaActualizacion = ahora;

        if (estado == null) {
            estado = EstadoEnvio.CREADO;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigoSeguimiento() {
        return codigoSeguimiento;
    }

    public String getRutRemitente() {
        return rutRemitente;
    }

    public String getRutDestinatario() {
        return rutDestinatario;
    }

    public String getDireccionOrigen() {
        return direccionOrigen;
    }

    public String getDireccionDestino() {
        return direccionDestino;
    }

    public BigDecimal getPesoKg() {
        return pesoKg;
    }

    public EstadoEnvio getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setCodigoSeguimiento(String codigoSeguimiento) {
        this.codigoSeguimiento = codigoSeguimiento;
    }

    public void setRutRemitente(String rutRemitente) {
        this.rutRemitente = rutRemitente;
    }

    public void setRutDestinatario(String rutDestinatario) {
        this.rutDestinatario = rutDestinatario;
    }

    public void setDireccionOrigen(String direccionOrigen) {
        this.direccionOrigen = direccionOrigen;
    }

    public void setDireccionDestino(String direccionDestino) {
        this.direccionDestino = direccionDestino;
    }

    public void setPesoKg(BigDecimal pesoKg) {
        this.pesoKg = pesoKg;
    }

    public void setEstado(EstadoEnvio estado) {
        this.estado = estado;
    }
}