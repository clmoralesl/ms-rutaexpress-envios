package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.*;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Region;
import cl.rutaexpress.envios.exception.AccesoDenegadoException;
import cl.rutaexpress.envios.exception.ComunaNoEncontradaException;
import cl.rutaexpress.envios.exception.EnvioNoEncontradoException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import cl.rutaexpress.envios.repository.RegionRepository;
import cl.rutaexpress.envios.security.PoliticaAcceso;
import cl.rutaexpress.envios.security.UsuarioActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EnvioService {

    private static final int MAX_INTENTOS_CODIGO = 5;

    private final EnvioRepository envioRepository;
    private final ComunaRepository comunaRepository;
    private final RegionRepository regionRepository;
    private final GeneradorCodigoSeguimiento generadorCodigoSeguimiento;
    private final NormalizadorDireccion normalizadorDireccion;
    private final CatalogoClient catalogoClient;
    private final PublicadorRabbitMQ publicadorRabbitMQ;
    private final PublicadorKafkaEnvios publicadorKafkaEnvios;
    private final PoliticaAcceso politicaAcceso;

    @Autowired
    public EnvioService(
            EnvioRepository envioRepository,
            ComunaRepository comunaRepository,
            RegionRepository regionRepository,
            GeneradorCodigoSeguimiento generadorCodigoSeguimiento,
            NormalizadorDireccion normalizadorDireccion,
            CatalogoClient catalogoClient,
            PublicadorRabbitMQ publicadorRabbitMQ,
            PublicadorKafkaEnvios publicadorKafkaEnvios,
            PoliticaAcceso politicaAcceso) {
        this.envioRepository = envioRepository;
        this.comunaRepository = comunaRepository;
        this.regionRepository = regionRepository;
        this.generadorCodigoSeguimiento = generadorCodigoSeguimiento;
        this.normalizadorDireccion = normalizadorDireccion;
        this.catalogoClient = catalogoClient;
        this.publicadorRabbitMQ = publicadorRabbitMQ;
        this.publicadorKafkaEnvios = publicadorKafkaEnvios;
        this.politicaAcceso = politicaAcceso;
    }

    public EnvioService(
            EnvioRepository envioRepository,
            ComunaRepository comunaRepository,
            RegionRepository regionRepository,
            GeneradorCodigoSeguimiento generadorCodigoSeguimiento,
            NormalizadorDireccion normalizadorDireccion,
            CatalogoClient catalogoClient,
            PublicadorRabbitMQ publicadorRabbitMQ,
            PublicadorKafkaEnvios publicadorKafkaEnvios) {
        this(
                envioRepository,
                comunaRepository,
                regionRepository,
                generadorCodigoSeguimiento,
                normalizadorDireccion,
                catalogoClient,
                publicadorRabbitMQ,
                publicadorKafkaEnvios,
                new PoliticaAcceso()
        );
    }

    @Transactional
    public EnvioResponse crear(CrearEnvioRequest request, UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        politicaAcceso.validarPuedeCrear(usuario);

        // 1. Resolver y validar existencia de Comunas en el catalogo CUT
        Comuna comunaOrigen = comunaRepository.findById(request.origen().idComuna())
                .orElseThrow(() -> new ComunaNoEncontradaException(request.origen().idComuna()));

        Comuna comunaDestino = comunaRepository.findById(request.destino().idComuna())
                .orElseThrow(() -> new ComunaNoEncontradaException(request.destino().idComuna()));

        // 2. Normalizar campos textuales (NFC, espacios, caracteres seguros, numero S/N)
        String calleOrigen = normalizadorDireccion.normalizarTexto(request.origen().calle(), "calleOrigen", true, 120);
        String numeroOrigen = normalizadorDireccion.normalizarNumero(request.origen().numero(), "numeroOrigen");
        String complementoOrigen = normalizadorDireccion.normalizarTexto(request.origen().complemento(), "complementoOrigen", false, 80);

        String calleDestino = normalizadorDireccion.normalizarTexto(request.destino().calle(), "calleDestino", true, 120);
        String numeroDestino = normalizadorDireccion.normalizarNumero(request.destino().numero(), "numeroDestino");
        String complementoDestino = normalizadorDireccion.normalizarTexto(request.destino().complemento(), "complementoDestino", false, 80);

        // 3. Generar codigo de seguimiento institucional unico (ignora cualquier valor enviado por el cliente)
        String codigoSeguimiento = generarCodigoUnicoConReintentos();

        // 4. Construir direcciones compuestas legadas para eventos y vistas
        String dirOrigenCompuesta = NormalizadorDireccion.componer(
                calleOrigen, numeroOrigen, complementoOrigen,
                comunaOrigen.getNombre(), comunaOrigen.getRegion().getNombre()
        );
        String dirDestinoCompuesta = NormalizadorDireccion.componer(
                calleDestino, numeroDestino, complementoDestino,
                comunaDestino.getNombre(), comunaDestino.getRegion().getNombre()
        );

        // 5. Persistir entidad con estructura normalizada 3FN y propietario
        Envio envio = new Envio(
                codigoSeguimiento,
                request.rutRemitente(),
                request.rutDestinatario(),
                calleOrigen,
                numeroOrigen,
                complementoOrigen,
                comunaOrigen,
                calleDestino,
                numeroDestino,
                complementoDestino,
                comunaDestino,
                request.pesoKg(),
                EstadoEnvio.CREADO
        );

        envio.setCreadoPorOid(usuario.oid());
        envio.setCreadoPorEmail(usuario.email());

        Envio envioGuardado = envioRepository.save(envio);

        String usuarioEmail = usuario.email();
        String usuarioRol = (usuario.roles() != null && !usuario.roles().isEmpty())
                ? String.join(",", usuario.roles()) : null;

        // 6. Publicacion de eventos post-commit para garantizar consistencia transaccional con usuario real
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicarEventoKafka(envioGuardado, null, EstadoEnvio.CREADO, usuarioEmail, usuarioRol, dirOrigenCompuesta, dirDestinoCompuesta);
                }
            });
        } else {
            publicarEventoKafka(envioGuardado, null, EstadoEnvio.CREADO, usuarioEmail, usuarioRol, dirOrigenCompuesta, dirDestinoCompuesta);
        }

        return EnvioResponse.fromEntity(envioGuardado, dirOrigenCompuesta, dirDestinoCompuesta);
    }

    public EnvioResponse obtenerPorId(Long id, UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        politicaAcceso.validarPuedeListar(usuario);

        Envio envio = buscarPorId(id);

        if (!politicaAcceso.puedeVerTodos(usuario)) {
            if (envio.getCreadoPorOid() == null || !envio.getCreadoPorOid().equals(usuario.oid())) {
                throw new AccesoDenegadoException("No tiene acceso a este envío");
            }
        }

        String dirOrigen = obtenerDireccionOrigenCompuesta(envio);
        String dirDestino = obtenerDireccionDestinoCompuesta(envio);
        return EnvioResponse.fromEntity(envio, dirOrigen, dirDestino);
    }

    public SeguimientoPublicoResponse obtenerSeguimientoPublico(String codigoSeguimiento) {
        Envio envio = envioRepository.findByCodigoSeguimiento(codigoSeguimiento)
                .orElseThrow(() -> new EnvioNoEncontradoException(codigoSeguimiento));

        String ciudadOrigen = (envio.getComunaOrigen() != null)
                ? envio.getComunaOrigen().getNombre() + ", " + envio.getComunaOrigen().getRegion().getNombre()
                : "No especificada";

        String ciudadDestino = (envio.getComunaDestino() != null)
                ? envio.getComunaDestino().getNombre() + ", " + envio.getComunaDestino().getRegion().getNombre()
                : "No especificada";

        LocalDateTime fechaEstimada = (envio.getFechaCreacion() != null)
                ? envio.getFechaCreacion().plusHours(24)
                : LocalDateTime.now().plusHours(24);
        String servicio = "Envío Express 24h";
        String courier = "Flota de Distribución RutaExpress";

        List<EventoSeguimientoDto> historial = construirHistorial(envio);

        return new SeguimientoPublicoResponse(
                envio.getCodigoSeguimiento(),
                envio.getEstado(),
                ciudadOrigen,
                ciudadDestino,
                fechaEstimada,
                servicio,
                courier,
                envio.getPesoKg(),
                envio.getFechaCreacion(),
                envio.getFechaActualizacion(),
                historial
        );
    }

    public List<EnvioResponse> listar(
            EstadoEnvio estado,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            UsuarioActual usuario) {

        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        politicaAcceso.validarPuedeListar(usuario);

        Specification<Envio> specification = Specification.where(null);

        if (estado != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("estado"), estado)
            );
        }

        if (fechaDesde != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.greaterThanOrEqualTo(
                                    root.get("fechaCreacion"),
                                    fechaDesde
                            )
            );
        }

        if (fechaHasta != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.lessThanOrEqualTo(
                                    root.get("fechaCreacion"),
                                    fechaHasta
                            )
            );
        }

        // Filtro por propietario a nivel de consulta SQL (D1, D3, D5)
        if (!politicaAcceso.puedeVerTodos(usuario)) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("creadoPorOid"), usuario.oid())
            );
        }

        return envioRepository.findAll(specification)
                .stream()
                .map(e -> EnvioResponse.fromEntity(
                        e,
                        obtenerDireccionOrigenCompuesta(e),
                        obtenerDireccionDestinoCompuesta(e)
                ))
                .toList();
    }

    @Transactional
    public EnvioResponse actualizarEstado(Long id, EstadoEnvio nuevoEstado, UsuarioActual usuario) {
        return actualizarEstado(id, nuevoEstado, null, usuario);
    }

    @Transactional
    public EnvioResponse actualizarEstado(
            Long id,
            EstadoEnvio nuevoEstado,
            Long servicioId,
            UsuarioActual usuario) {

        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        politicaAcceso.validarPuedeGestionar(usuario);

        String usuarioEmail = usuario.email();
        String usuarioRol = (usuario.roles() != null && !usuario.roles().isEmpty())
                ? String.join(",", usuario.roles()) : null;

        return actualizarEstadoInterno(id, nuevoEstado, servicioId, usuarioEmail, usuarioRol);
    }

    private EnvioResponse actualizarEstadoInterno(
            Long id,
            EstadoEnvio nuevoEstado,
            Long servicioId,
            String usuarioEmail,
            String usuarioRol) {

        Envio envio = buscarPorId(id);

        validarTransicion(envio.getEstado(), nuevoEstado);

        EstadoEnvio estadoAnterior = envio.getEstado();

        // Si la transicion es hacia ACEPTADO, descontar capacidad vehicular en el catalogo
        if (nuevoEstado == EstadoEnvio.ACEPTADO) {
            Long idServicio = (servicioId != null) ? servicioId : 1L;
            catalogoClient.descontarCapacidad(idServicio);
        }

        envio.setEstado(nuevoEstado);

        Envio envioGuardado = envioRepository.save(envio);

        String dirOrigen = obtenerDireccionOrigenCompuesta(envioGuardado);
        String dirDestino = obtenerDireccionDestinoCompuesta(envioGuardado);

        // Publicacion post-commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicarEventosRabbitMQ(envioGuardado, nuevoEstado, dirOrigen, dirDestino);
                    publicarEventoKafka(envioGuardado, estadoAnterior, nuevoEstado, usuarioEmail, usuarioRol, dirOrigen, dirDestino);
                }
            });
        } else {
            publicarEventosRabbitMQ(envioGuardado, nuevoEstado, dirOrigen, dirDestino);
            publicarEventoKafka(envioGuardado, estadoAnterior, nuevoEstado, usuarioEmail, usuarioRol, dirOrigen, dirDestino);
        }

        return EnvioResponse.fromEntity(envioGuardado, dirOrigen, dirDestino);
    }

    @Transactional
    public void solicitarGeneracionEtiqueta(Long id, UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        politicaAcceso.validarPuedeSolicitarEtiqueta(usuario);

        Envio envio = buscarPorId(id);
        String correlationId = envio.getCodigoSeguimiento();
        String dirOrigen = obtenerDireccionOrigenCompuesta(envio);
        String dirDestino = obtenerDireccionDestinoCompuesta(envio);

        EtiquetaPayload payloadEtiqueta = new EtiquetaPayload(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                envio.getRutRemitente(),
                envio.getRutDestinatario(),
                dirOrigen,
                dirDestino,
                envio.getPesoKg(),
                "PDF_TERMICO_10X15",
                LocalDateTime.now(),
                envio.getCalleOrigen(),
                envio.getNumeroOrigen(),
                envio.getComplementoOrigen(),
                envio.getComunaOrigen() != null ? envio.getComunaOrigen().getNombre() : null,
                (envio.getComunaOrigen() != null && envio.getComunaOrigen().getRegion() != null) ? envio.getComunaOrigen().getRegion().getNombre() : null,
                envio.getCalleDestino(),
                envio.getNumeroDestino(),
                envio.getComplementoDestino(),
                envio.getComunaDestino() != null ? envio.getComunaDestino().getNombre() : null,
                (envio.getComunaDestino() != null && envio.getComunaDestino().getRegion() != null) ? envio.getComunaDestino().getRegion().getNombre() : null
        );

        MensajeEnvelope<EtiquetaPayload> mensajeEtiqueta = MensajeEnvelope.de(
                "ETIQUETA_ROTULADO_GENERAR",
                correlationId,
                payloadEtiqueta
        );
        publicadorRabbitMQ.publicar(
                RabbitMQConfig.EXCHANGE_DIRECT,
                RabbitMQConfig.ROUTING_KEY_LABEL,
                mensajeEtiqueta
        );
    }

    public List<RegionConComunasResponse> listarCatalogoGeografico() {
        List<Region> regiones = regionRepository.findAllByOrderByIdRegionAsc();
        return regiones.stream()
                .map(r -> new RegionConComunasResponse(
                        r.getIdRegion(),
                        r.getNombre(),
                        r.getComunas().stream()
                                .map(c -> new RegionConComunasResponse.ComunaItemResponse(
                                         c.getIdComuna(),
                                         c.getNombre()
                                ))
                                .toList()
                ))
                .toList();
    }

    private String generarCodigoUnicoConReintentos() {
        for (int intento = 0; intento < MAX_INTENTOS_CODIGO; intento++) {
            String codigo = generadorCodigoSeguimiento.generar();
            if (!envioRepository.existsByCodigoSeguimiento(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No fue posible generar un código de seguimiento único tras " + MAX_INTENTOS_CODIGO + " reintentos");
    }

    private String obtenerDireccionOrigenCompuesta(Envio envio) {
        String comuna = (envio.getComunaOrigen() != null) ? envio.getComunaOrigen().getNombre() : "";
        String region = (envio.getComunaOrigen() != null && envio.getComunaOrigen().getRegion() != null)
                ? envio.getComunaOrigen().getRegion().getNombre() : "";
        return NormalizadorDireccion.componer(
                envio.getCalleOrigen(),
                envio.getNumeroOrigen(),
                envio.getComplementoOrigen(),
                comuna,
                region
        );
    }

    private String obtenerDireccionDestinoCompuesta(Envio envio) {
        String comuna = (envio.getComunaDestino() != null) ? envio.getComunaDestino().getNombre() : "";
        String region = (envio.getComunaDestino() != null && envio.getComunaDestino().getRegion() != null)
                ? envio.getComunaDestino().getRegion().getNombre() : "";
        return NormalizadorDireccion.componer(
                envio.getCalleDestino(),
                envio.getNumeroDestino(),
                envio.getComplementoDestino(),
                comuna,
                region
        );
    }

    private void publicarEventoKafka(
            Envio envio,
            EstadoEnvio estadoAnterior,
            EstadoEnvio estadoNuevo,
            String usuarioEmail,
            String usuarioRol,
            String dirOrigen,
            String dirDestino) {
        EventoEnvioKafka evento = EventoEnvioKafka.deTransicion(
                envio,
                estadoAnterior,
                estadoNuevo,
                usuarioEmail,
                usuarioRol,
                dirOrigen,
                dirDestino,
                null
        );
        publicadorKafkaEnvios.publicarEvento(evento);
    }

    private void publicarEventosRabbitMQ(Envio envio, EstadoEnvio nuevoEstado, String dirOrigen, String dirDestino) {
        String correlationId = envio.getCodigoSeguimiento();

        String comDestino = (envio.getComunaDestino() != null) ? envio.getComunaDestino().getNombre() : null;
        String regDestino = (envio.getComunaDestino() != null && envio.getComunaDestino().getRegion() != null) ? envio.getComunaDestino().getRegion().getNombre() : null;
        String comOrigen = (envio.getComunaOrigen() != null) ? envio.getComunaOrigen().getNombre() : null;
        String regOrigen = (envio.getComunaOrigen() != null && envio.getComunaOrigen().getRegion() != null) ? envio.getComunaOrigen().getRegion().getNombre() : null;

        if (nuevoEstado == EstadoEnvio.ACEPTADO) {
            // 1. Mensaje de notificacion por email
            NotificacionEmailPayload payloadEmail = new NotificacionEmailPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    nuevoEstado,
                    "Envio Aceptado - " + envio.getCodigoSeguimiento(),
                    "Su envio ha sido aceptado y se encuentra en preparacion.",
                    LocalDateTime.now()
            );
            MensajeEnvelope<NotificacionEmailPayload> mensajeEmail = MensajeEnvelope.de(
                    "EMAIL_ENVIO_ACEPTADO",
                    correlationId,
                    payloadEmail
            );
            publicadorRabbitMQ.publicar(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_EMAIL,
                    mensajeEmail
            );

            // 2. Mensaje para ticket de picking en bodega con datos estructurados
            TicketBodegaPayload payloadBodega = new TicketBodegaPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    dirOrigen,
                    dirDestino,
                    envio.getPesoKg(),
                    LocalDateTime.now(),
                    "Generar ticket de picking y rotulado para recepcion en bodega",
                    envio.getCalleOrigen(),
                    envio.getNumeroOrigen(),
                    envio.getComplementoOrigen(),
                    comOrigen,
                    regOrigen,
                    envio.getCalleDestino(),
                    envio.getNumeroDestino(),
                    envio.getComplementoDestino(),
                    comDestino,
                    regDestino
            );
            MensajeEnvelope<TicketBodegaPayload> mensajeBodega = MensajeEnvelope.de(
                    "TICKET_BODEGA_GENERAR",
                    correlationId,
                    payloadBodega
            );
            publicadorRabbitMQ.publicar(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_WAREHOUSE,
                    mensajeBodega
            );
        } else if (nuevoEstado == EstadoEnvio.EN_BODEGA) {
            EtiquetaPayload payloadEtiqueta = new EtiquetaPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    dirOrigen,
                    dirDestino,
                    envio.getPesoKg(),
                    "PDF_TERMICO_10X15",
                    LocalDateTime.now(),
                    envio.getCalleOrigen(),
                    envio.getNumeroOrigen(),
                    envio.getComplementoOrigen(),
                    comOrigen,
                    regOrigen,
                    envio.getCalleDestino(),
                    envio.getNumeroDestino(),
                    envio.getComplementoDestino(),
                    comDestino,
                    regDestino
            );
            MensajeEnvelope<EtiquetaPayload> mensajeEtiqueta = MensajeEnvelope.de(
                    "ETIQUETA_ROTULADO_GENERAR",
                    correlationId,
                    payloadEtiqueta
            );
            publicadorRabbitMQ.publicar(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_LABEL,
                    mensajeEtiqueta
            );
        } else if (nuevoEstado == EstadoEnvio.EN_RUTA) {
            NotificacionEmailPayload payloadEmail = new NotificacionEmailPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    nuevoEstado,
                    "Envio en Ruta - " + envio.getCodigoSeguimiento(),
                    "Su envio se encuentra en ruta hacia la direccion de destino.",
                    LocalDateTime.now()
            );
            MensajeEnvelope<NotificacionEmailPayload> mensajeEmail = MensajeEnvelope.de(
                    "EMAIL_ENVIO_EN_RUTA",
                    correlationId,
                    payloadEmail
            );
            publicadorRabbitMQ.publicar(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_EMAIL,
                    mensajeEmail
            );
        } else if (nuevoEstado == EstadoEnvio.ENTREGADO) {
            NotificacionEmailPayload payloadEmail = new NotificacionEmailPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    nuevoEstado,
                    "Envio Entregado - " + envio.getCodigoSeguimiento(),
                    "Su envio ha sido entregado exitosamente en la direccion de destino.",
                    LocalDateTime.now()
            );
            MensajeEnvelope<NotificacionEmailPayload> mensajeEmail = MensajeEnvelope.de(
                    "EMAIL_ENVIO_ENTREGADO",
                    correlationId,
                    payloadEmail
            );
            publicadorRabbitMQ.publicar(
                    RabbitMQConfig.EXCHANGE_DIRECT,
                    RabbitMQConfig.ROUTING_KEY_EMAIL,
                    mensajeEmail
            );
        }
    }

    private Envio buscarPorId(Long id) {
        return envioRepository.findById(id)
                .orElseThrow(() -> new EnvioNoEncontradoException(id));
    }

    private void validarTransicion(
            EstadoEnvio estadoActual,
            EstadoEnvio nuevoEstado) {

        boolean valida =
                (estadoActual == EstadoEnvio.CREADO
                        && (nuevoEstado == EstadoEnvio.ACEPTADO
                        || nuevoEstado == EstadoEnvio.CANCELADO))
                ||
                (estadoActual == EstadoEnvio.ACEPTADO
                        && (nuevoEstado == EstadoEnvio.EN_BODEGA
                        || nuevoEstado == EstadoEnvio.CANCELADO))
                ||
                (estadoActual == EstadoEnvio.EN_BODEGA
                        && (nuevoEstado == EstadoEnvio.EN_RUTA
                        || nuevoEstado == EstadoEnvio.CANCELADO))
                ||
                (estadoActual == EstadoEnvio.EN_RUTA
                        && nuevoEstado == EstadoEnvio.ENTREGADO);

        if (!valida) {
            throw new TransicionEstadoInvalidaException(
                    estadoActual,
                    nuevoEstado
            );
        }
    }

    private List<EventoSeguimientoDto> construirHistorial(Envio envio) {
        List<EventoSeguimientoDto> eventos = new ArrayList<>();
        LocalDateTime creacion = (envio.getFechaCreacion() != null) ? envio.getFechaCreacion() : LocalDateTime.now();
        LocalDateTime actualizacion = (envio.getFechaActualizacion() != null) ? envio.getFechaActualizacion() : creacion;

        eventos.add(new EventoSeguimientoDto(EstadoEnvio.CREADO, "Envío registrado en plataforma", creacion));

        if (envio.getEstado() == EstadoEnvio.CANCELADO) {
            eventos.add(new EventoSeguimientoDto(EstadoEnvio.CANCELADO, "Envío cancelado", actualizacion));
            return eventos;
        }

        if (envio.getEstado() == EstadoEnvio.ACEPTADO
                || envio.getEstado() == EstadoEnvio.EN_BODEGA
                || envio.getEstado() == EstadoEnvio.EN_RUTA
                || envio.getEstado() == EstadoEnvio.ENTREGADO) {
            LocalDateTime fechaAceptado = (envio.getEstado() == EstadoEnvio.ACEPTADO) ? actualizacion : creacion.plusMinutes(45);
            eventos.add(new EventoSeguimientoDto(EstadoEnvio.ACEPTADO, "Envío aceptado y clasificado en centro logístico", fechaAceptado));
        }

        if (envio.getEstado() == EstadoEnvio.EN_BODEGA
                || envio.getEstado() == EstadoEnvio.EN_RUTA
                || envio.getEstado() == EstadoEnvio.ENTREGADO) {
            LocalDateTime fechaBodega = (envio.getEstado() == EstadoEnvio.EN_BODEGA) ? actualizacion : creacion.plusHours(2);
            eventos.add(new EventoSeguimientoDto(EstadoEnvio.EN_BODEGA, "Recepción y rotulado de paquete en bodega central", fechaBodega));
        }

        if (envio.getEstado() == EstadoEnvio.EN_RUTA
                || envio.getEstado() == EstadoEnvio.ENTREGADO) {
            LocalDateTime fechaRuta = (envio.getEstado() == EstadoEnvio.EN_RUTA) ? actualizacion : creacion.plusHours(5);
            eventos.add(new EventoSeguimientoDto(EstadoEnvio.EN_RUTA, "En transporte hacia la dirección de destino", fechaRuta));
        }

        if (envio.getEstado() == EstadoEnvio.ENTREGADO) {
            eventos.add(new EventoSeguimientoDto(EstadoEnvio.ENTREGADO, "Paquete entregado exitosamente en destino", actualizacion));
        }

        return eventos;
    }
}
