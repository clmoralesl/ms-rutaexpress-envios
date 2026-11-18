package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
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

    /**
     * Lectura con bloqueo pesimista de escritura (SELECT ... FOR UPDATE WAIT 3 en Oracle).
     * Sin EntityGraph para evitar JOIN dentro del FOR UPDATE; las comunas se cargan de forma perezosa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select e from Envio e where e.id = :id")
    Optional<Envio> findByIdParaActualizar(@Param("id") Long id);
}
