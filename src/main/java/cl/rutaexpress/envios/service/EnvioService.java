package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.EtiquetaPayload;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.dto.NotificacionEmailPayload;
import cl.rutaexpress.envios.dto.TicketBodegaPayload;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.EnvioNoEncontradoException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.EnvioRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EnvioService {

    private final EnvioRepository envioRepository;
    private final CatalogoClient catalogoClient;
    private final PublicadorRabbitMQ publicadorRabbitMQ;
    private final PublicadorKafkaEnvios publicadorKafkaEnvios;

    public EnvioService(
            EnvioRepository envioRepository,
            CatalogoClient catalogoClient,
            PublicadorRabbitMQ publicadorRabbitMQ,
            PublicadorKafkaEnvios publicadorKafkaEnvios) {
        this.envioRepository = envioRepository;
        this.catalogoClient = catalogoClient;
        this.publicadorRabbitMQ = publicadorRabbitMQ;
        this.publicadorKafkaEnvios = publicadorKafkaEnvios;
    }

    @Transactional
    public EnvioResponse crear(CrearEnvioRequest request) {
        return crear(request, null, null);
    }

    @Transactional
    public EnvioResponse crear(CrearEnvioRequest request, String usuarioEmail, String usuarioRol) {

        Envio envio = new Envio(
                request.codigoSeguimiento(),
                request.rutRemitente(),
                request.rutDestinatario(),
                request.direccionOrigen(),
                request.direccionDestino(),
                request.pesoKg(),
                EstadoEnvio.CREADO
        );

        Envio envioGuardado = envioRepository.save(envio);

        // Publicar evento inmutable de creacion a Kafka (shipments.events)
        publicarEventoKafka(envioGuardado, null, EstadoEnvio.CREADO, usuarioEmail, usuarioRol);

        return EnvioResponse.fromEntity(envioGuardado);
    }

    public EnvioResponse obtenerPorId(Long id) {
        return EnvioResponse.fromEntity(buscarPorId(id));
    }

    public List<EnvioResponse> listar(
            EstadoEnvio estado,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta) {

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

        return envioRepository.findAll(specification)
                .stream()
                .map(EnvioResponse::fromEntity)
                .toList();
    }

    @Transactional
    public EnvioResponse actualizarEstado(Long id, EstadoEnvio nuevoEstado) {
        return actualizarEstado(id, nuevoEstado, null, null, null);
    }

    @Transactional
    public EnvioResponse actualizarEstado(Long id, EstadoEnvio nuevoEstado, Long servicioId) {
        return actualizarEstado(id, nuevoEstado, servicioId, null, null);
    }

    @Transactional
    public EnvioResponse actualizarEstado(
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

        // Publicar eventos correspondientes a RabbitMQ
        publicarEventosRabbitMQ(envioGuardado, nuevoEstado);

        // Publicar evento de dominio a Kafka (shipments.events)
        publicarEventoKafka(envioGuardado, estadoAnterior, nuevoEstado, usuarioEmail, usuarioRol);

        return EnvioResponse.fromEntity(envioGuardado);
    }

    @Transactional
    public void solicitarGeneracionEtiqueta(Long id) {
        Envio envio = buscarPorId(id);
        String correlationId = envio.getCodigoSeguimiento();

        EtiquetaPayload payloadEtiqueta = new EtiquetaPayload(
                envio.getId(),
                envio.getCodigoSeguimiento(),
                envio.getRutRemitente(),
                envio.getRutDestinatario(),
                envio.getDireccionOrigen(),
                envio.getDireccionDestino(),
                envio.getPesoKg(),
                "PDF_TERMICO_10X15",
                LocalDateTime.now()
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

    private void publicarEventoKafka(
            Envio envio,
            EstadoEnvio estadoAnterior,
            EstadoEnvio estadoNuevo,
            String usuarioEmail,
            String usuarioRol) {
        EventoEnvioKafka evento = EventoEnvioKafka.deTransicion(
                envio,
                estadoAnterior,
                estadoNuevo,
                usuarioEmail,
                usuarioRol,
                null
        );
        publicadorKafkaEnvios.publicarEvento(evento);
    }

    private void publicarEventosRabbitMQ(Envio envio, EstadoEnvio nuevoEstado) {
        String correlationId = envio.getCodigoSeguimiento();

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

            // 2. Mensaje para ticket de picking en bodega
            TicketBodegaPayload payloadBodega = new TicketBodegaPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    envio.getDireccionOrigen(),
                    envio.getDireccionDestino(),
                    envio.getPesoKg(),
                    LocalDateTime.now(),
                    "Generar ticket de picking y rotulado para recepcion en bodega"
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
            // Emision de solicitud de rotulado termico con QR hacia q.cmd.label
            EtiquetaPayload payloadEtiqueta = new EtiquetaPayload(
                    envio.getId(),
                    envio.getCodigoSeguimiento(),
                    envio.getRutRemitente(),
                    envio.getRutDestinatario(),
                    envio.getDireccionOrigen(),
                    envio.getDireccionDestino(),
                    envio.getPesoKg(),
                    "PDF_TERMICO_10X15",
                    LocalDateTime.now()
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
}