package co.edu.ufpso.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Clave HS256 desde {@code JWT_SECRET} y firma y lectura de tokens. Sin Spring ni MongoDB. */
class JwtConfigTest {

    private static final String SECRETO = "evWQxEq/XTs3zPWacy3VjEFC/I4tQ/J3Q5SvjxlIyBA=";
    private static final String OTRO_SECRETO = Base64.getEncoder().encodeToString(new byte[32]);

    private final JwtConfig config = new JwtConfig();

    @Test
    void aceptaUnaClaveDe32BytesEnBase64() {
        SecretKey clave = JwtConfig.claveDesdeBase64(SECRETO);
        assertThat(clave.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(clave.getEncoded()).hasSize(32);
    }

    @Test
    void rechazaClaveVaciaNoBase64OCortaSinMostrarla() {
        assertThatThrownBy(() -> JwtConfig.claveDesdeBase64(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> JwtConfig.claveDesdeBase64("esto no es base64!"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("esto no es base64");
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> JwtConfig.claveDesdeBase64(corta))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tiene 16");
    }

    @Test
    void elTokenFirmadoSeLeeConLaMismaClave() {
        SecretKey clave = JwtConfig.claveDesdeBase64(SECRETO);
        Instant ahora = Instant.now();
        String token = firmar(config.jwtEncoder(clave), ahora, ahora.plusSeconds(900));

        Jwt leido = config.jwtDecoder(clave).decode(token);

        assertThat(leido.getSubject()).isEqualTo("6750a1b2c3d4e5f601234567");
        assertThat(leido.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        Instant ahora = Instant.now();
        String token = firmar(config.jwtEncoder(JwtConfig.claveDesdeBase64(OTRO_SECRETO)), ahora,
                ahora.plusSeconds(900));
        JwtDecoder decoder = config.jwtDecoder(JwtConfig.claveDesdeBase64(SECRETO));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rechazaUnTokenVencido() {
        SecretKey clave = JwtConfig.claveDesdeBase64(SECRETO);
        Instant hace1Hora = Instant.now().minusSeconds(3600);
        String token = firmar(config.jwtEncoder(clave), hace1Hora, hace1Hora.plusSeconds(900));

        assertThatThrownBy(() -> config.jwtDecoder(clave).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void elToStringDeLasPropiedadesNoMuestraElSecreto() {
        PropiedadesJwt propiedades = new PropiedadesJwt(SECRETO, null, null);
        assertThat(propiedades.toString()).doesNotContain(SECRETO).contains("***");
    }

    static String firmar(JwtEncoder encoder, Instant emitido, Instant expira) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("6750a1b2c3d4e5f601234567")
                .issuedAt(emitido)
                .expiresAt(expira)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
