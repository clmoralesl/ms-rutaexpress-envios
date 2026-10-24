package cl.rutaexpress.envios.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class GeneradorCodigoSeguimientoTest {

    private static final Pattern PATRON_CROCKFORD =
            Pattern.compile("^RTX-\\d{4}-[0-9A-HJKMNP-TV-Z]{6}$");

    @Test
    @DisplayName("El formato del código cumple con regex estricto Crockford Base32 de 15 caracteres")
    void testFormatoCodigoSeguimiento() {
        Clock clockFijo = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("America/Santiago"));
        GeneradorCodigoSeguimiento generador = new GeneradorCodigoSeguimiento(clockFijo);

        for (int i = 0; i < 50; i++) {
            String codigo = generador.generar();
            assertNotNull(codigo);
            assertEquals(15, codigo.length());
            assertTrue(codigo.startsWith("RTX-2026-"));
            assertTrue(PATRON_CROCKFORD.matcher(codigo).matches(), "Código no cumple patrón: " + codigo);
            assertFalse(codigo.contains("I"), "No debe contener letra I");
            assertFalse(codigo.contains("L"), "No debe contener letra L");
            assertFalse(codigo.contains("O"), "No debe contener letra O");
            assertFalse(codigo.contains("U"), "No debe contener letra U");
        }
    }

    @Test
    @DisplayName("El año del código cambia según el Clock inyectado")
    void testAnoConClockFijo() {
        Clock clock2027 = Clock.fixed(Instant.parse("2027-06-15T12:00:00Z"), ZoneId.of("America/Santiago"));
        GeneradorCodigoSeguimiento generador = new GeneradorCodigoSeguimiento(clock2027);

        String codigo = generador.generar();
        assertTrue(codigo.startsWith("RTX-2027-"));
    }
}
