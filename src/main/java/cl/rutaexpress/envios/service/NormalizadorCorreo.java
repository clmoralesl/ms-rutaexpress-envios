package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.exception.CorreoInvalidoException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza correos para compararlos: strip, NFC y minusculas (Locale.ROOT).
 * No elimina puntos ni la etiqueta +algo: esas reglas dependen del proveedor.
 */
public final class NormalizadorCorreo {

    public static final int LARGO_MAXIMO = 254;

    // Cualquier espacio en blanco, incluidos U+00A0 (no separable) y U+2007, U+202F
    private static final Pattern ESPACIO_INTERNO = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]");
    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

    private NormalizadorCorreo() {
    }

    public static String normalizar(String correo) {
        if (correo == null) {
            throw new CorreoInvalidoException("El correo es obligatorio");
        }
        String limpio = correo.strip();
        limpio = Normalizer.normalize(limpio, Normalizer.Form.NFC);
        limpio = limpio.toLowerCase(Locale.ROOT);

        if (limpio.isEmpty()) {
            throw new CorreoInvalidoException("El correo es obligatorio");
        }
        if (ESPACIO_INTERNO.matcher(limpio).find()) {
            throw new CorreoInvalidoException("El correo no puede contener espacios");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new CorreoInvalidoException("El correo no puede superar " + LARGO_MAXIMO + " caracteres");
        }
        if (!FORMATO.matcher(limpio).matches()) {
            throw new CorreoInvalidoException("El correo no tiene un formato válido");
        }
        return limpio;
    }

    /**
     * Variante que no lanza: devuelve null si el correo es nulo o invalido.
     * Se usa con el correo del token, que el usuario no controla.
     */
    public static String normalizarOpcional(String correo) {
        if (correo == null) {
            return null;
        }
        try {
            return normalizar(correo);
        } catch (CorreoInvalidoException e) {
            return null;
        }
    }
}
