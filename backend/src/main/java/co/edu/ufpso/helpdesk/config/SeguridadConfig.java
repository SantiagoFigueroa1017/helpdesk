package co.edu.ufpso.helpdesk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import co.edu.ufpso.helpdesk.security.ConvertidorJwtUsuario;
import co.edu.ufpso.helpdesk.security.ErroresSeguridad;
import co.edu.ufpso.helpdesk.security.PropiedadesLimiteLogin;

/**
 * Cadena de filtros de Spring Security (contexto 8.3, acta 2.4).
 *
 * <ul>
 *   <li>Sin sesión de servidor: cada petición trae su access token en {@code Authorization: Bearer}.</li>
 *   <li>El resource server valida el token con el {@code JwtDecoder} de {@code security.JwtConfig}, y
 *       {@code security.ConvertidorJwtUsuario} arma el usuario con sus permisos efectivos.</li>
 *   <li>Solo los tres endpoints de autenticación son públicos. Todo lo demás exige token.</li>
 *   <li>{@code @EnableMethodSecurity} activa {@code @PreAuthorize("hasAuthority('...')")}.</li>
 *   <li>CORS con el {@code CorsConfigurationSource} de {@code CorsConfig}. Va antes de la
 *       autenticación, así la consulta previa ({@code OPTIONS}) del navegador no pide token.</li>
 * </ul>
 *
 * <p>CSRF va desactivado. La API autentica con la cabecera {@code Authorization}, que el navegador
 * no agrega solo. Las excepciones son {@code /api/auth/refresh} y {@code /api/auth/logout}, que leen
 * el refresh token de una cookie {@code SameSite=Strict}: el navegador no la envía en peticiones
 * que nacen en otro sitio.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesLimiteLogin.class)
public class SeguridadConfig {

    /** Rutas públicas: las atiende {@code auth.controller.AuthController}. */
    static final String[] RUTAS_AUTH = { "/api/auth/login", "/api/auth/refresh", "/api/auth/logout" };

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ErroresSeguridad errores,
            ConvertidorJwtUsuario convertidor) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, RUTAS_AUTH).permitAll()
                        // Boot reenvía aquí los errores que no atrapa el manejador global.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(errores))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }

    /** BCrypt, el mismo formato de los hashes de la semilla y del validador de usuarios. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
