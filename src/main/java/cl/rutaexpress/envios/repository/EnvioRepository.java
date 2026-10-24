package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvioRepository
        extends JpaRepository<Envio, Long>,
                JpaSpecificationExecutor<Envio> {

    @Override
    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    Optional<Envio> findById(Long id);

    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    Optional<Envio> findByCodigoSeguimiento(String codigoSeguimiento);

    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    List<Envio> findByRutRemitente(String rutRemitente);

    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    List<Envio> findByEstado(EstadoEnvio estado);

    @Override
    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    List<Envio> findAll(Specification<Envio> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"comunaOrigen", "comunaOrigen.region", "comunaDestino", "comunaDestino.region"})
    List<Envio> findAll(Specification<Envio> spec);

    boolean existsByCodigoSeguimiento(String codigoSeguimiento);
}
