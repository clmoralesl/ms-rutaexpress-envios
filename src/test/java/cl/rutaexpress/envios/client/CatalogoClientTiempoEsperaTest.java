package cl.rutaexpress.envios.client;

import cl.rutaexpress.envios.TestEnvioFactory;
import cl.rutaexpress.envios.exception.CatalogoNoDisponibleException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Usa un servidor HTTP real del JDK y la fabrica de produccion del cliente, para comprobar el tiempo
 * de lectura. El tiempo de conexion (2 s) solo es configuracion: no se puede verificar de forma fiable en local.
 */
class CatalogoClientTiempoEsperaTest {

    private static final String SECRETO = "test-secreto-interno-envios-seguro-32caracteres";

    private HttpServer servidor;

    @AfterEach
    void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void lecturaLentaSeCorta() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/api/catalogo/cotizaciones", ex -> {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        servidor.start();

        CatalogoClient cliente = new CatalogoClient("http://127.0.0.1:" + servidor.getAddress().getPort(), SECRETO, 2000, 3000);

        long inicio = System.nanoTime();
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.cotizar(TestEnvioFactory.solicitudCotizacion()));
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(ms >= 2500 && ms < 5000, "duracion " + ms + " ms");
    }

    @Test
    void conexionRechazada() throws Exception {
        HttpServer temporal = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int puerto = temporal.getAddress().getPort();
        temporal.stop(0);

        CatalogoClient cliente = new CatalogoClient("http://127.0.0.1:" + puerto, SECRETO, 2000, 3000);

        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.cotizar(TestEnvioFactory.solicitudCotizacion()));
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.descontarCapacidad(1L));
    }
}
