package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.EnvioBloqueadoException;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.publisher.PublicadorKafkaEnvios;
import cl.rutaexpress.envios.publisher.PublicadorRabbitMQ;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;
import cl.rutaexpress.envios.service.EnvioService;
import cl.rutaexpress.envios.testutil.UsuariosPrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sin @Transactional en la clase: cada hilo ejecuta su propia transaccion de aceptar.
 * H2 no reproduce FOR UPDATE WAIT 3 de Oracle; el tiempo de espera real se comprueba en Oracle.
 */
@SpringBootTest
class AceptacionConcurrenciaTest {

    @Autowired
    private EnvioService envioService;

    @Autowired
    private EnvioRepository envioRepository;

    @Autowired
    private ComunaRepository comunaRepository;

    @MockBean
    private CatalogoClient catalogoClient;

    @MockBean
    private PublicadorRabbitMQ publicadorRabbitMQ;

    @MockBean
    private PublicadorKafkaEnvios publicadorKafkaEnvios;

    @BeforeEach
    @AfterEach
    void limpiar() {
        envioRepository.deleteAll();
    }

    @Test
    @DisplayName("C11: dos aceptaciones simultaneas descuentan un solo cupo y solo una tiene exito")
    void dosAceptacionesSimultaneasDescuentanUnSoloCupo() throws Exception {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 2L);
        when(catalogoClient.cotizar(any())).thenAnswer(inv -> {
            Thread.sleep(400);
            return TestEnvioFactory.cotizacion(2L, 1L, 6000L);
        });

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Object> tarea = () -> {
            salida.await();
            try {
                return envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador());
            } catch (RuntimeException e) {
                return e;
            }
        };
        Future<Object> f1 = pool.submit(tarea);
        Future<Object> f2 = pool.submit(tarea);
        salida.countDown();
        List<Object> r = List.of(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, r.stream().filter(EnvioResponse.class::isInstance).count(), "resultados: " + r);
        assertTrue(r.stream().filter(o -> !(o instanceof EnvioResponse))
                        .allMatch(o -> o instanceof TransicionEstadoInvalidaException || o instanceof EnvioBloqueadoException),
                "resultados: " + r);
        verify(catalogoClient, times(1)).descontarCapacidad(2L);
        assertEquals(EstadoEnvio.ACEPTADO, envioRepository.findById(id).orElseThrow().getEstado());
    }
}
