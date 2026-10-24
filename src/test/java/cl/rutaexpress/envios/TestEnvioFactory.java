package cl.rutaexpress.envios;

import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.DireccionRequest;

import java.math.BigDecimal;

public final class TestEnvioFactory {

    private TestEnvioFactory() {}

    public static CrearEnvioRequest crearRequestValido() {
        return crearRequestValido("11111111-1", "22222222-2", new BigDecimal("3.00"));
    }

    public static CrearEnvioRequest crearRequestValido(String rutRemitente, String rutDestinatario, BigDecimal pesoKg) {
        return new CrearEnvioRequest(
                null,
                rutRemitente,
                rutDestinatario,
                new DireccionRequest("Av. Providencia", "1234", "Oficina 401", 13123),
                new DireccionRequest("Calle Los Aromos", "456", null, 13101),
                pesoKg
        );
    }

    public static CrearEnvioRequest crearRequestConComunaInexistente(int idComunaInexistente) {
        return new CrearEnvioRequest(
                null,
                "11111111-1",
                "22222222-2",
                new DireccionRequest("Av. Providencia", "1234", null, idComunaInexistente),
                new DireccionRequest("Calle Los Aromos", "456", null, 13101),
                new BigDecimal("2.50")
        );
    }
}
