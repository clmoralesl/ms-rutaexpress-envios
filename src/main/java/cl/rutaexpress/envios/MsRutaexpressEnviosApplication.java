package cl.rutaexpress.envios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
public class MsRutaexpressEnviosApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsRutaexpressEnviosApplication.class, args);
	}

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("America/Santiago"));
	}
}
