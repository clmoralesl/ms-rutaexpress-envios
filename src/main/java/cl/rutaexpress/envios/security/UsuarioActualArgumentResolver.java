package cl.rutaexpress.envios.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class UsuarioActualArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UsuarioActual.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String oid = request.getHeader("X-Usuario-Oid");

        if (oid == null || oid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta cabecera requerida de identidad: X-Usuario-Oid");
        }

        String email = request.getHeader("X-Usuario-Email");
        String rolesHeader = request.getHeader("X-Usuario-Roles");

        Set<String> roles;
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            roles = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            roles = Collections.emptySet();
        }

        return new UsuarioActual(oid.trim(), (email != null && !email.isBlank()) ? email.trim() : null, roles);
    }
}
