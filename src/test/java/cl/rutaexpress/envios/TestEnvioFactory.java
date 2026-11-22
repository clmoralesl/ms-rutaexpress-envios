package cl.rutaexpress.envios;

import cl.rutaexpress.envios.dto.AceptarEnvioRequest;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.DireccionRequest;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoRequest;
import cl.rutaexpress.envios.dto.catalogo.CotizacionCatalogoResponse;
import cl.rutaexpress.envios.entity.Comuna;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.entity.InstantaneaPrecio;
import cl.rutaexpress.envios.entity.Medidas;
import cl.rutaexpress.envios.repository.ComunaRepository;
import cl.rutaexpress.envios.repository.EnvioRepository;

import java.math.BigDecimal;
import java.util.UUID;

public final class TestEnvioFactory {

    public static final Long SUCURSAL_POR_DEFECTO = 1L;
    public static final Long SERVICIO_POR_DEFECTO = 1L;

    private TestEnvioFactory() {}

    public static CrearEnvioRequest crearRequestValido() {
        return crearRequestValido("11111111-1", "22222222-2", new BigDecimal("3.00"));
    }

    public static CrearEnvioRequest crearRequestValido(String rutRemitente, String rutDestinatario, BigDecimal pesoKg) {
        return crearRequest(rutRemitente, rutDestinatario, pesoKg, SERVICIO_POR_DEFECTO, SUCURSAL_POR_DEFECTO, "remitente@empresa.cl");
    }

    public static CrearEnvioRequest crearRequest(
            String rutRemitente,
            String rutDestinatario,
            BigDecimal pesoKg,
            Long servicioId,
            Long sucursalId,
            String remitenteEmail) {
        return new CrearEnvioRequest(
                null,
                rutRemitente,
                rutDestinatario,
                sucursalId,
                servicioId,
                new DireccionRequest("Calle Los Aromos", "456", null, 13101),
                pesoKg,
                null,
                null,
                null,
                remitenteEmail
        );
    }

    public static CrearEnvioRequest crearRequestConComunaInexistente(int idComunaInexistente) {
        return new CrearEnvioRequest(
                null,
                "11111111-1",
                "22222222-2",
                SUCURSAL_POR_DEFECTO,
                SERVICIO_POR_DEFECTO,
                new DireccionRequest("Calle Los Aromos", "456", null, idComunaInexistente),
                new BigDecimal("2.50"),
                null,
                null,
                null,
                "remitente@empresa.cl"
        );
    }

    /** Cotizacion del catalogo con la sucursal en Providencia (13123). */
    public static CotizacionCatalogoResponse cotizacion(Long servicioId, Long sucursalId, long totalClp) {
        return new CotizacionCatalogoResponse(
                servicioId,
                sucursalId,
                13123,
                "MISMA_REGION",
                new BigDecimal("1.15"),
                new BigDecimal("3000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("3.00"),
                totalClp,
                new CotizacionCatalogoResponse.DireccionSucursal("Providencia", "Av. Providencia", "2045", "Local 4", 13123)
        );
    }

    public static CotizacionCatalogoRequest solicitudCotizacion() {
        return new CotizacionCatalogoRequest(
                SERVICIO_POR_DEFECTO,
                SUCURSAL_POR_DEFECTO,
                13101,
                new BigDecimal("3.00"),
                null,
                true
        );
    }

    public static AceptarEnvioRequest aceptacionValida() {
        return new AceptarEnvioRequest(new BigDecimal("4.50"), 30, 20, 10, null);
    }

    /** Persiste un envio CREADO pre-registrado (sucursal 1, peso 3.00, precio estimado) y devuelve su id. */
    public static Long persistirPreregistrado(EnvioRepository envioRepository, ComunaRepository comunaRepository, Long servicioId) {
        Comuna origen = comunaRepository.findById(13123).orElseThrow();
        Comuna destino = comunaRepository.findById(13101).orElseThrow();
        Envio envio = new Envio(
                "RTX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "11111111-1",
                "22222222-2",
                "Av. Providencia", "2045", "Local 4", origen,
                "Calle Los Aromos", "456", null, destino,
                new BigDecimal("3.00"),
                EstadoEnvio.CREADO
        );
        envio.setCreadoPorOid("00000000-0000-0000-0000-despachador1");
        envio.setCreadoPorEmail("despachador@rutaexpress.cl");
        envio.setSucursalOrigenId(SUCURSAL_POR_DEFECTO);
        envio.setServicioId(servicioId);
        envio.setMedidasDeclaradas(new Medidas(30, 20, 10));
        envio.setPrecioEstimado(InstantaneaPrecio.desde(cotizacion(servicioId, SUCURSAL_POR_DEFECTO, 6000L)));
        envio.setRemitenteEmail("remitente@empresa.cl");
        return envioRepository.save(envio).getId();
    }
}
