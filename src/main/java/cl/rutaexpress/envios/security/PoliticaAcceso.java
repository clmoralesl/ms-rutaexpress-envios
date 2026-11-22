package cl.rutaexpress.envios.security;

import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.exception.AccesoDenegadoException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PoliticaAcceso {

    private boolean tieneRol(Set<String> roles, String... nombres) {
        if (roles == null) return false;
        for (String nombre : nombres) {
            if (roles.contains(nombre)) return true;
        }
        return false;
    }

    public boolean puedeVerTodos(UsuarioActual usuario) {
        if (usuario == null || usuario.roles() == null) {
            return false;
        }
        return tieneRol(usuario.roles(), "ADMIN", "DESPACHADOR", "OPERADOR");
    }

    /**
     * Visibilidad de un envio concreto. El personal con vista total ve todo. El resto ve el envio si
     * el oid coincide con el creador, o si el correo del token (ya normalizado, no nulo) coincide
     * con el correo del remitente guardado.
     */
    public boolean puedeVerEnvio(UsuarioActual usuario, Envio envio, String correoNormalizado) {
        if (usuario == null || envio == null) {
            return false;
        }
        if (puedeVerTodos(usuario)) {
            return true;
        }
        if (envio.getCreadoPorOid() != null && envio.getCreadoPorOid().equals(usuario.oid())) {
            return true;
        }
        return correoNormalizado != null && correoNormalizado.equals(envio.getRemitenteEmail());
    }

    public boolean puedeCrear(UsuarioActual usuario) {
        if (usuario == null || usuario.roles() == null) {
            return false;
        }
        return tieneRol(usuario.roles(), "ADMIN", "DESPACHADOR", "CLIENTE");
    }

    public boolean puedeGestionar(UsuarioActual usuario) {
        if (usuario == null || usuario.roles() == null) {
            return false;
        }
        return tieneRol(usuario.roles(), "ADMIN", "DESPACHADOR");
    }

    public boolean puedeSolicitarEtiqueta(UsuarioActual usuario) {
        if (usuario == null || usuario.roles() == null) {
            return false;
        }
        return tieneRol(usuario.roles(), "ADMIN", "DESPACHADOR", "OPERADOR");
    }

    public void validarPuedeListar(UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        if (usuario.roles() == null || usuario.roles().isEmpty()) {
            throw new AccesoDenegadoException("Usuario no posee roles asignados para operar en envíos");
        }
        if (!puedeVerTodos(usuario) && !usuario.roles().contains("CLIENTE")) {
            throw new AccesoDenegadoException("Rol no autorizado para acceder al listado de envíos");
        }
    }

    public void validarPuedeCrear(UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        if (!puedeCrear(usuario)) {
            throw new AccesoDenegadoException("No tiene permisos para crear envíos");
        }
    }

    public void validarPuedeGestionar(UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        if (!puedeGestionar(usuario)) {
            throw new AccesoDenegadoException("No tiene permisos para modificar el estado del envío");
        }
    }

    public void validarPuedeSolicitarEtiqueta(UsuarioActual usuario) {
        if (usuario == null) {
            throw new AccesoDenegadoException("Identidad de usuario requerida");
        }
        if (!puedeSolicitarEtiqueta(usuario)) {
            throw new AccesoDenegadoException("No tiene permisos para solicitar la etiqueta del envío");
        }
    }
}
