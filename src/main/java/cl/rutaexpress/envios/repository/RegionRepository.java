package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.Region;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegionRepository extends JpaRepository<Region, Integer> {

    @EntityGraph(attributePaths = {"comunas"})
    List<Region> findAllByOrderByIdRegionAsc();
}
