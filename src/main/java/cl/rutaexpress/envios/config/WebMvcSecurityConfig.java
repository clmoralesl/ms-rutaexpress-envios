package cl.rutaexpress.envios.config;

import cl.rutaexpress.envios.security.AutenticacionInternaInterceptor;
import cl.rutaexpress.envios.security.UsuarioActualArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final AutenticacionInternaInterceptor autenticacionInternaInterceptor;

    public WebMvcSecurityConfig(AutenticacionInternaInterceptor autenticacionInternaInterceptor) {
        this.autenticacionInternaInterceptor = autenticacionInternaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(autenticacionInternaInterceptor)
                .addPathPatterns("/api/envios/**")
                .excludePathPatterns(
                        "/api/envios/publico/seguimiento/**",
                        "/actuator/health/**"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioActualArgumentResolver());
    }
}
