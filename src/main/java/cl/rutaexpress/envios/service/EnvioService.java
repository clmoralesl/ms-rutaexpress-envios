package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.EnvioNoEncontradoException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
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

    public EnvioService(EnvioRepository envioRepository) {
        this.envioRepository = envioRepository;
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

        Envio envio = buscarPorId(id);

        validarTransicion(envio.getEstado(), nuevoEstado);

        envio.setEstado(nuevoEstado);

        return EnvioResponse.fromEntity(envioRepository.save(envio));
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