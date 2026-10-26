package cl.rutaexpress.envios.security;

import java.util.Set;

public record UsuarioActual(
        String oid,
        String email,
        Set<String> roles
) {
}
