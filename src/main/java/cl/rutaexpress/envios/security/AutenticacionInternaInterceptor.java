package cl.rutaexpress.envios.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class AutenticacionInternaInterceptor implements HandlerInterceptor {

    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final Set<String> MARCADORES_INVALIDOS = Set.of(
            "xxxxxxxxxxxx",
            "changeme",
            "secret",
            "secreto",
            "12345678"
    );

    private final String secretoEsperado;

    public AutenticacionInternaInterceptor(
            @Value("${rutaexpress.secreto.interno:#{null}}") String secretoEsperado) {
        this.secretoEsperado = secretoEsperado;
    }

    @PostConstruct
    public void validarConfiguracionSecreto() {
        if (secretoEsperado == null || secretoEsperado.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CRÍTICO: La propiedad rutaexpress.secreto.interno no está configurada o se encuentra vacía. ms-rutaexpress-envios no puede iniciar sin autenticación interna configurada."
            );
        }
        String limpio = secretoEsperado.trim();
        if (limpio.length() < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException(
                    "CRÍTICO: El secreto compartido interno debe tener una longitud mínima de " + LONGITUD_MINIMA_SECRETO + " caracteres."
            );
        }
        if (MARCADORES_INVALIDOS.contains(limpio.toLowerCase())) {
            throw new IllegalStateException(
                    "CRÍTICO: El secreto compartido interno configurado contiene un valor de ejemplo o marcador no permitido."
            );
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String cabeceraSecreto = request.getHeader("X-Servicio-Interno");

        if (cabeceraSecreto == null || cabeceraSecreto.isBlank()) {
            responderNoAutorizado(response, "Acceso no autorizado: falta cabecera X-Servicio-Interno");
            return false;
        }

        byte[] esperadoBytes = secretoEsperado.trim().getBytes(StandardCharsets.UTF_8);
        byte[] recibidoBytes = cabeceraSecreto.trim().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(esperadoBytes, recibidoBytes)) {
            responderNoAutorizado(response, "Acceso no autorizado: credencial interna invalida");
            return false;
        }

        return true;
    }

    private void responderNoAutorizado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }
}
