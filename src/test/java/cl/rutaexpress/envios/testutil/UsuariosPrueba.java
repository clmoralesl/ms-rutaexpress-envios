package cl.rutaexpress.envios.testutil;

import cl.rutaexpress.envios.security.UsuarioActual;

import java.util.Set;

public final class UsuariosPrueba {

    private UsuariosPrueba() {}

    public static UsuarioActual admin() {
        return new UsuarioActual("00000000-0000-0000-0000-admin0000001", "admin@rutaexpress.cl", Set.of("ADMIN"));
    }

    public static UsuarioActual despachador() {
        return new UsuarioActual("00000000-0000-0000-0000-despachador1", "despachador@rutaexpress.cl", Set.of("DESPACHADOR"));
    }

    public static UsuarioActual operador() {
        return new UsuarioActual("00000000-0000-0000-0000-operador001", "operador@rutaexpress.cl", Set.of("OPERADOR"));
    }

    public static UsuarioActual cliente(String oid, String email) {
        return new UsuarioActual(oid, email, Set.of("CLIENTE"));
    }

    public static UsuarioActual clienteA() {
        return cliente("00000000-0000-0000-0000-cliente00001", "cliente.a@empresa.cl");
    }

    public static UsuarioActual clienteB() {
        return cliente("00000000-0000-0000-0000-cliente00002", "cliente.b@empresa.cl");
    }

    public static UsuarioActual auditor() {
        return new UsuarioActual("00000000-0000-0000-0000-auditor00001", "auditor@rutaexpress.cl", Set.of("AUDITOR"));
    }
}
