package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByCodigoSeguimiento(String codigoSeguimiento);

    List<Shipment> findByRutRemitente(String rutRemitente);

    List<Shipment> findByEstado(EstadoEnvio estado);

    boolean existsByCodigoSeguimiento(String codigoSeguimiento);
}