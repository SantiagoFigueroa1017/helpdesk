package co.edu.ufpso.helpdesk.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema en UTC (acta 2.4). Los servicios piden la hora a este bean y no a
 * {@code Instant.now()}, así una prueba puede fijar la hora.
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
