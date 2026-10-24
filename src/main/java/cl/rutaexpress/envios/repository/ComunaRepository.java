package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.Comuna;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComunaRepository extends JpaRepository<Comuna, Integer> {

    @EntityGraph(attributePaths = {"region"})
    Optional<Comuna> findById(Integer id);

    List<Comuna> findByRegionIdRegionOrderByNombreAsc(Integer idRegion);
}
