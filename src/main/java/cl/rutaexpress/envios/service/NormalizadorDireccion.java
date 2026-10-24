package cl.rutaexpress.envios.service;

import cl.rutaexpress.envios.exception.DireccionInvalidaException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class NormalizadorDireccion {

    private static final Pattern PATRON_CARACTERES_PERMITIDOS =
            Pattern.compile("^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ\\s\\.,'\\-#/º°]+$");

    private static final Pattern PATRON_CONTROL =
            Pattern.compile("[\\p{Cntrl}]");

    private static final Pattern PATRON_SIN_NUMERO =
            Pattern.compile("^(?i)S/?N$|^SN$");

    private static final Pattern PATRON_CONTIENE_DIGITO =
            Pattern.compile(".*\\d.*");

    public String normalizarTexto(String texto, String nombreCampo, boolean obligatorio, int longitudMaxima) {
        if (texto == null || texto.isBlank()) {
            if (obligatorio) {
                throw new DireccionInvalidaException("El campo " + nombreCampo + " es obligatorio y no puede estar vacío");
            }
            return null;
        }

        // 1. Normalización Unicode NFC
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFC);

        // 2. Rechazar caracteres de control
        if (PATRON_CONTROL.matcher(normalizado).find()) {
            throw new DireccionInvalidaException("El campo " + nombreCampo + " contiene caracteres de control no permitidos");
        }

        // 3. Neutralizar comas reemplazándolas por espacio en todos los campos para evitar inconsistencias en composiciones
        normalizado = normalizado.replace(',', ' ');

        // 4. Colapsar espacios múltiples y recortar extremos
        normalizado = normalizado.replaceAll("\\s+", " ").trim();

        // 5. Validar caracteres seguros permitidos
        if (!PATRON_CARACTERES_PERMITIDOS.matcher(normalizado).matches()) {
            throw new DireccionInvalidaException("El campo " + nombreCampo + " contiene caracteres no válidos para una dirección postal");
        }

        // 6. Validar longitud máxima
        if (normalizado.length() > longitudMaxima) {
            throw new DireccionInvalidaException("El campo " + nombreCampo + " excede el límite de " + longitudMaxima + " caracteres");
        }

        return normalizado;
    }

    public String normalizarNumero(String numero, String nombreCampo) {
        String num = normalizarTexto(numero, nombreCampo, true, 20);

        if (PATRON_SIN_NUMERO.matcher(num).matches()) {
            return "S/N";
        }

        if (!PATRON_CONTIENE_DIGITO.matcher(num).matches()) {
            throw new DireccionInvalidaException("El campo " + nombreCampo + " debe contener al menos un dígito numérico o especificar 'S/N'");
        }

        return num;
    }

    public static String componer(String calle, String numero, String complemento, String nombreComuna, String nombreRegion) {
        StringBuilder sb = new StringBuilder();
        if (calle != null) {
            sb.append(calle);
        }
        if (numero != null && !numero.isBlank()) {
            sb.append(" ").append(numero);
        }

        if (complemento != null && !complemento.isBlank()) {
            sb.append(", ").append(complemento.trim());
        }

        if (nombreComuna != null && !nombreComuna.isBlank()) {
            sb.append(", ").append(nombreComuna.trim());
        }

        if (nombreRegion != null && !nombreRegion.isBlank()) {
            sb.append(", ").append(nombreRegion.trim());
        }

        return sb.toString();
    }
}
