package cl.rutaexpress.envios;

import cl.rutaexpress.envios.client.CatalogoClient;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
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
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Sin @Transactional en la clase: el rollback debe ocurrir dentro de aceptar para que
 * afterCompletion dispare la compensacion.
 */
@SpringBootTest
class AceptacionCompensacionTest {

    @Autowired
    private EnvioService envioService;

    @SpyBean
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
    @DisplayName("C12: un fallo despues del descuento restablece el cupo y el envio sigue CREADO")
    void falloDespuesDelDescuentoRestableceElCupo() {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(5L, 1L, 6000L));
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(envioRepository).saveAndFlush(any(Envio.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador()));

        InOrder orden = inOrder(catalogoClient);
        orden.verify(catalogoClient).descontarCapacidad(5L);
        orden.verify(catalogoClient).restablecerCapacidad(5L);
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
        verifyNoInteractions(publicadorKafkaEnvios);
    }

    @Test
    @DisplayName("C13: si la compensacion falla, se propaga el error original")
    void falloDeLaCompensacionNoOcultaElErrorOriginal() {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(5L, 1L, 6000L));
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(envioRepository).saveAndFlush(any(Envio.class));
        doThrow(new CatalogoNoDisponibleException("caido")).when(catalogoClient).restablecerCapacidad(5L);

        assertThrows(DataIntegrityViolationException.class,
                () -> envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador()));

        verify(catalogoClient).restablecerCapacidad(5L);
    }

    @Test
    @DisplayName("C14: si el descuento responde 409 no hay compensacion")
    void sinDescuentoNoHayCompensacion() {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(5L, 1L, 6000L));
        doThrow(new CapacidadFlotaAgotadaException(5L)).when(catalogoClient).descontarCapacidad(5L);

        assertThrows(CapacidadFlotaAgotadaException.class,
                () -> envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador()));

        verify(catalogoClient, never()).restablecerCapacidad(any());
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("D6c: si el descuento termina por tiempo de espera (resultado incierto) no se compensa")
    void descuentoConResultadoIncierto() {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(5L, 1L, 6000L));
        doThrow(new CatalogoNoDisponibleException("tiempo agotado")).when(catalogoClient).descontarCapacidad(5L);

        assertThrows(CatalogoNoDisponibleException.class,
                () -> envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador()));

        verify(catalogoClient, never()).restablecerCapacidad(any());
        assertEquals(EstadoEnvio.CREADO, envioRepository.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("C15: una aceptacion exitosa no restablece el cupo")
    void aceptacionExitosaNoRestablece() {
        Long id = TestEnvioFactory.persistirPreregistrado(envioRepository, comunaRepository, 5L);
        when(catalogoClient.cotizar(any())).thenReturn(TestEnvioFactory.cotizacion(5L, 1L, 6000L));

        envioService.aceptar(id, TestEnvioFactory.aceptacionValida(), UsuariosPrueba.despachador());

        verify(catalogoClient).descontarCapacidad(5L);
        verify(catalogoClient, never()).restablecerCapacidad(any());
        assertEquals(EstadoEnvio.ACEPTADO, envioRepository.findById(id).orElseThrow().getEstado());
    }
}
