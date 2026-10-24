package cl.rutaexpress.envios.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Year;

@Component
public class GeneradorCodigoSeguimiento {

    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LONGITUD = 6;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public GeneradorCodigoSeguimiento(Clock clock) {
        this.clock = clock;
    }

    public String generar() {
        StringBuilder sb = new StringBuilder("RTX-")
                .append(Year.now(clock).getValue())
                .append('-');
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO[random.nextInt(ALFABETO.length)]);
        }
        return sb.toString();
    }
}
