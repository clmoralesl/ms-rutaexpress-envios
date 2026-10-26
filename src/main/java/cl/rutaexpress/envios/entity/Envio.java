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

    @Column(name = "codigo_seguimiento", nullable = false, unique = true, length = 50, updatable = false)
    private String codigoSeguimiento;

    @Column(name = "rut_remitente", nullable = false, length = 20)
    private String rutRemitente;

    @Column(name = "rut_destinatario", nullable = false, length = 20)
    private String rutDestinatario;

    @Column(name = "calle_origen", nullable = false, length = 120)
    private String calleOrigen;

    @Column(name = "numero_origen", nullable = false, length = 20)
    private String numeroOrigen;

    @Column(name = "complemento_origen", length = 80)
    private String complementoOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comuna_origen", nullable = false)
    private Comuna comunaOrigen;

    @Column(name = "calle_destino", nullable = false, length = 120)
    private String calleDestino;

    @Column(name = "numero_destino", nullable = false, length = 20)
    private String numeroDestino;

    @Column(name = "complemento_destino", length = 80)
    private String complementoDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comuna_destino", nullable = false)
    private Comuna comunaDestino;

    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pesoKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoEnvio estado;

    @Column(name = "creado_por_oid", length = 36, updatable = false)
    private String creadoPorOid;

    @Column(name = "creado_por_email", length = 320, updatable = false)
    private String creadoPorEmail;

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
        this(
                codigoSeguimiento,
                rutRemitente,
                rutDestinatario,
                direccionOrigen != null ? direccionOrigen : "Origen",
                "S/N",
                null,
                new Comuna(13101, "Santiago", new Region(13, "Metropolitana de Santiago")),
                direccionDestino != null ? direccionDestino : "Destino",
                "S/N",
                null,
                new Comuna(13123, "Providencia", new Region(13, "Metropolitana de Santiago")),
                pesoKg,
                estado
        );
    }

    public Envio(
            String codigoSeguimiento,
            String rutRemitente,
            String rutDestinatario,
            String calleOrigen,
            String numeroOrigen,
            String complementoOrigen,
            Comuna comunaOrigen,
            String calleDestino,
            String numeroDestino,
            String complementoDestino,
            Comuna comunaDestino,
            BigDecimal pesoKg,
            EstadoEnvio estado
    ) {
        this.codigoSeguimiento = codigoSeguimiento;
        this.rutRemitente = rutRemitente;
        this.rutDestinatario = rutDestinatario;
        this.calleOrigen = calleOrigen;
        this.numeroOrigen = numeroOrigen;
        this.complementoOrigen = complementoOrigen;
        this.comunaOrigen = comunaOrigen;
        this.calleDestino = calleDestino;
        this.numeroDestino = numeroDestino;
        this.complementoDestino = complementoDestino;
        this.comunaDestino = comunaDestino;
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

    public String getCalleOrigen() {
        return calleOrigen;
    }

    public String getNumeroOrigen() {
        return numeroOrigen;
    }

    public String getComplementoOrigen() {
        return complementoOrigen;
    }

    public Comuna getComunaOrigen() {
        return comunaOrigen;
    }

    public String getCalleDestino() {
        return calleDestino;
    }

    public String getNumeroDestino() {
        return numeroDestino;
    }

    public String getComplementoDestino() {
        return complementoDestino;
    }

    public Comuna getComunaDestino() {
        return comunaDestino;
    }

    public BigDecimal getPesoKg() {
        return pesoKg;
    }

    public EstadoEnvio getEstado() {
        return estado;
    }

    public String getCreadoPorOid() {
        return creadoPorOid;
    }

    public void setCreadoPorOid(String creadoPorOid) {
        this.creadoPorOid = creadoPorOid;
    }

    public String getCreadoPorEmail() {
        return creadoPorEmail;
    }

    public void setCreadoPorEmail(String creadoPorEmail) {
        this.creadoPorEmail = creadoPorEmail;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setEstado(EstadoEnvio estado) {
        this.estado = estado;
    }
}
