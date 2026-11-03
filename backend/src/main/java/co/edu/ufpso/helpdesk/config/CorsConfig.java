package co.edu.ufpso.helpdesk.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS restringido al origen del frontend (contexto 8.3, acta 2.4).
 *
 * <p>En desarrollo el frontend llama a la API por el proxy de Vite, desde el mismo origen, y CORS no
 * interviene. Esta configuración aplica cuando el frontend y el backend corren en orígenes
 * distintos. Los orígenes salen de {@code helpdesk.cors.origenes}, separados por comas.
 *
 * <ul>
 *   <li>{@code allowCredentials}: el navegador envía la cookie del refresh token. Por eso los
 *       orígenes van uno por uno; con credenciales no se admite {@code *}.</li>
 *   <li>Se exponen {@code Location} (respuestas 201) y {@code Retry-After} (429 del login) para que
 *       el JavaScript del frontend las pueda leer.</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${helpdesk.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes.stream().map(String::strip).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}
