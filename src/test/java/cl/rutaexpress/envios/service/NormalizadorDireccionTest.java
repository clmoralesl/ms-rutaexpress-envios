package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.exception.DireccionInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizadorDireccionTest {

    private final NormalizadorDireccion normalizador = new NormalizadorDireccion();

    @Test
    @DisplayName("Colapsa espacios múltiples y aplica trim")
    void testColapsarEspacios() {
        String resultado = normalizador.normalizarTexto("   Av.   Providencia    1234   ", "calle", true, 120);
        assertEquals("Av. Providencia 1234", resultado);
    }

    @Test
    @DisplayName("Neutraliza comas en calle y complemento reemplazándolas por espacio")
    void testNeutralizaComasEnCalleYComplemento() {
        String calle = normalizador.normalizarTexto("Av. Libertador Bernardo O'Higgins, 1234", "calle", true, 120);
        assertEquals("Av. Libertador Bernardo O'Higgins 1234", calle);
        assertFalse(calle.contains(","));

        String complemento = normalizador.normalizarTexto("Edificio Titanium, Piso 14, Oficina 1402", "complemento", false, 80);
        assertEquals("Edificio Titanium Piso 14 Oficina 1402", complemento);
        assertFalse(complemento.contains(","));
    }

    @Test
    @DisplayName("Acepta S/N en mayúsculas o minúsculas y lo normaliza a S/N")
    void testNormalizarNumeroSinNumero() {
        assertEquals("S/N", normalizador.normalizarNumero("s/n", "numero"));
        assertEquals("S/N", normalizador.normalizarNumero("S/N", "numero"));
        assertEquals("S/N", normalizador.normalizarNumero("SN", "numero"));
        assertEquals("S/N", normalizador.normalizarNumero("sn", "numero"));
        assertEquals("1234", normalizador.normalizarNumero("1234", "numero"));
        assertEquals("45-B", normalizador.normalizarNumero("45-B", "numero"));
    }

    @Test
    @DisplayName("Rechaza números sin dígitos que no sean S/N")
    void testRechazaNumeroSinDigitos() {
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarNumero("abc", "numero"));
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarNumero("---", "numero"));
    }

    @Test
    @DisplayName("Lanza excepción si campo obligatorio es nulo o vacío")
    void testCampoObligatorioVacio() {
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarTexto("", "calle", true, 120));
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarTexto(null, "calle", true, 120));
    }

    @Test
    @DisplayName("Rechaza caracteres de control")
    void testRechazaCaracteresDeControl() {
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarTexto("Calle\nInvalida", "calle", true, 120));
        assertThrows(DireccionInvalidaException.class, () ->
                normalizador.normalizarTexto("Calle\tTab", "calle", true, 120));
    }

    @Test
    @DisplayName("Composición correcta de dirección completa")
    void testComponerDireccion() {
        String completaConComplemento = normalizador.componer(
                "Av. Providencia", "1234", "Depto 502", "Providencia", "Metropolitana de Santiago"
        );
        assertEquals("Av. Providencia 1234, Depto 502, Providencia, Metropolitana de Santiago", completaConComplemento);

        String completaSinComplemento = normalizador.componer(
                "Calle Los Aromos", "456", null, "Maipú", "Metropolitana de Santiago"
        );
        assertEquals("Calle Los Aromos 456, Maipú, Metropolitana de Santiago", completaSinComplemento);
    }
}
