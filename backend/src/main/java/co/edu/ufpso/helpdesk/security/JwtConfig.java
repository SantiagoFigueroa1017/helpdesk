package co.edu.ufpso.helpdesk.security;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Firma y lectura de los access tokens con HS256 (acta 2.4).
 *
 * <p>El mismo backend emite y verifica los tokens, así que basta una clave simétrica. El
 * {@link JwtDecoder} lo usa el resource server de Spring Security en cada petición: valida la
 * firma y la expiración ({@code exp}). El {@link JwtEncoder} lo usa el login para emitir el token.
 * Al definir aquí el {@code JwtDecoder}, Boot no arma el suyo ni crea el usuario de prueba con
 * contraseña generada.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesJwt.class)
public class JwtConfig {

    /** HS256 exige una clave de al menos 256 bits. */
    static final int BYTES_MINIMOS = 32;

    @Bean
    SecretKey claveJwt(PropiedadesJwt propiedades) {
        return claveDesdeBase64(propiedades.secreto());
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey claveJwt) {
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return NimbusJwtEncoder.withSecretKey(claveJwt).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Convierte {@code JWT_SECRET} en la clave HMAC. Si falta o es corta, el backend no arranca:
     * es preferible a firmar con una clave débil. El mensaje nunca incluye el valor.
     */
    static SecretKey claveDesdeBase64(String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException("Falta helpdesk.jwt.secreto (variable JWT_SECRET en backend/.env).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET no es Base64 válido. Genérelo como indica .env.example.");
        }
        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + BYTES_MINIMOS
                    + " bytes; tiene " + bytes.length + ". Genérelo como indica .env.example.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
