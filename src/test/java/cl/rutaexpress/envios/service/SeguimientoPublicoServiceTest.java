package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.SeguimientoPublicoResponse;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.EnvioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeguimientoPublicoServiceTest {

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @Mock
    private PublicadorKafkaEnvios publicadorKafkaEnvios;

    private EnvioService envioService;

    @BeforeEach
    void setUp() {
        envioService = new EnvioService(
                envioRepository,
                catalogoClient,
                publicadorRabbitMQ,
                publicadorKafkaEnvios
        );
    }

    @Test
    void seguimientoExistenteDevuelveDatosPublicos() throws Exception {
        String codigo = "RE-TEST-001";

        Envio envio = new Envio(
                codigo,
                "11111111-1",
                "22222222-2",
                "Av. Providencia 1234",
                "Av. Apoquindo 5678",
                new BigDecimal("2.50"),
                EstadoEnvio.EN_RUTA
        );

        // Simula el callback JPA que asigna las fechas al persistir.
        var metodoPrePersist = Envio.class.getDeclaredMethod("prePersist");
        metodoPrePersist.setAccessible(true);
        metodoPrePersist.invoke(envio);

        when(envioRepository.findByCodigoSeguimiento(codigo))
                .thenReturn(Optional.of(envio));

        SeguimientoPublicoResponse respuesta =
                envioService.obtenerSeguimientoPorCodigo(codigo);

        assertEquals(codigo, respuesta.codigoSeguimiento());
        assertEquals(EstadoEnvio.EN_RUTA, respuesta.estado());
        assertNotNull(respuesta.fechaCreacion());
        assertNotNull(respuesta.fechaActualizacion());

        assertEquals(4,
                SeguimientoPublicoResponse.class.getRecordComponents().length);

        verify(envioRepository).findByCodigoSeguimiento(codigo);
    }

    @Test
    void seguimientoInexistenteDevuelve404() {
        String codigo = "RE-NO-EXISTE";

        when(envioRepository.findByCodigoSeguimiento(codigo))
                .thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> envioService.obtenerSeguimientoPorCodigo(codigo)
        );

        assertEquals(HttpStatus.NOT_FOUND, excepcion.getStatusCode());
    }
}