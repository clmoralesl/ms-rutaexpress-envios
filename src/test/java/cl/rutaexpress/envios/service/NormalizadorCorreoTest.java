package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.exception.CorreoInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizadorCorreoTest {

    @Test
    void capitalizacionYEspaciosExternos() {
        assertEquals("ana.perez+envios@empresa.cl", NormalizadorCorreo.normalizar("  Ana.Perez+Envios@EMPRESA.CL \t"));
    }

    @Test
    void conservaPuntosYEtiqueta() {
        assertEquals("a.b+x@empresa.cl", NormalizadorCorreo.normalizar("A.B+X@empresa.cl"));
    }

    @Test
    void aplicaNfc() {
        // "e" mas acento combinante (U+0301) debe componerse en "e con acento" (U+00E9)
        assertEquals("jos\u00e9@empresa.cl", NormalizadorCorreo.normalizar("jose\u0301@empresa.cl"));
    }

    @Test
    void rechazaEspacioInterno() {
        assertThrows(CorreoInvalidoException.class, () -> NormalizadorCorreo.normalizar("ana perez@empresa.cl"));
        assertThrows(CorreoInvalidoException.class, () -> NormalizadorCorreo.normalizar("ana\u00A0perez@empresa.cl"));
    }

    @Test
    void limiteDe254() {
        String base = "a".repeat(64) + "@" + "b".repeat(186) + ".cl";
        assertEquals(254, NormalizadorCorreo.normalizar(base).length());
        assertThrows(CorreoInvalidoException.class, () -> NormalizadorCorreo.normalizar("c" + base));
    }

    @Test
    void rechazaFormatosInvalidos() {
        for (String s : List.of("sin-arroba.cl", "a@b", "a@@b.cl", "@empresa.cl", "")) {
            assertThrows(CorreoInvalidoException.class, () -> NormalizadorCorreo.normalizar(s), s);
        }
        assertThrows(CorreoInvalidoException.class, () -> NormalizadorCorreo.normalizar(null));
    }

    @Test
    void parecidosNoCoinciden() {
        assertNotEquals(NormalizadorCorreo.normalizar("ana@empresa.com"), NormalizadorCorreo.normalizar("ana@empresa.cl"));
    }

    @Test
    void opcionalNoLanza() {
        assertNull(NormalizadorCorreo.normalizarOpcional("no valido"));
        assertNull(NormalizadorCorreo.normalizarOpcional(null));
        assertEquals("ana@empresa.cl", NormalizadorCorreo.normalizarOpcional(" ANA@empresa.cl"));
    }
}
