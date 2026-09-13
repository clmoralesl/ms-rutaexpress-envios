package cl.rutaexpress.envios.repository;

import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.Envio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EnvioRepository
        extends JpaRepository<Envio, Long>,
                JpaSpecificationExecutor<Envio> {

    Optional<Envio> findByCodigoSeguimiento(String codigoSeguimiento);

    List<Envio> findByRutRemitente(String rutRemitente);

    List<Envio> findByEstado(EstadoEnvio estado);

    boolean existsByCodigoSeguimiento(String codigoSeguimiento);
}