package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.config.RabbitMQConfig;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.dto.MensajeEnvelope;
import cl.rutaexpress.envios.dto.NotificacionEmailPayload;
import cl.rutaexpress.envios.dto.TicketBodegaPayload;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.EnvioNoEncontradoException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
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

    public EnvioService(
            EnvioRepository envioRepository,
            CatalogoClient catalogoClient,
            PublicadorRabbitMQ publicadorRabbitMQ) {
        this.envioRepository = envioRepository;
        this.catalogoClient = catalogoClient;
        this.publicadorRabbitMQ = publicadorRabbitMQ;
    }

    @Transactional
    public EnvioResponse crear(CrearEnvioRequest request) {

        Envio envio = new Envio(
                request.codigoSeguimiento(),
                request.rutRemitente(),
                request.rutDestinatario(),
                request.direccionOrigen(),
                request.direccionDestino(),
                request.pesoKg(),
                EstadoEnvio.CREADO
        );

        return EnvioResponse.fromEntity(envioRepository.save(envio));
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
        return actualizarEstado(id, nuevoEstado, null);
    }

    @Transactional
    public EnvioResponse actualizarEstado(Long id, EstadoEnvio nuevoEstado, Long servicioId) {

        Envio envio = buscarPorId(id);

        validarTransicion(envio.getEstado(), nuevoEstado);

        // Si la transicion es hacia ACEPTADO, descontar capacidad vehicular en el catalogo
        if (nuevoEstado == EstadoEnvio.ACEPTADO) {
            Long idServicio = (servicioId != null) ? servicioId : 1L;
            catalogoClient.descontarCapacidad(idServicio);
        }

        envio.setEstado(nuevoEstado);

        Envio envioGuardado = envioRepository.save(envio);

        // Publicar eventos correspondientes a RabbitMQ
        publicarEventosRabbitMQ(envioGuardado, nuevoEstado);

        return EnvioResponse.fromEntity(envioGuardado);
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