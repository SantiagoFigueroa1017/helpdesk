package co.edu.ufpso.helpdesk.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Activa {@code @Cacheable} y {@code @CacheEvict} (acta 2.4). Boot arma el {@code CacheManager} de
 * Caffeine con {@code spring.cache.*} de {@code application.yml}: cachés {@code usuariosSesion} y
 * {@code rolesSesion}, hasta 5.000 entradas cada una y 10 minutos de vida.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
