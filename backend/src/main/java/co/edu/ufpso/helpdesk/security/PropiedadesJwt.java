package co.edu.ufpso.helpdesk.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code helpdesk.jwt.*} de {@code application.yml} (acta 2.4).
 *
 * @param secreto          clave HS256 en Base64, 32 bytes o más. Sale de {@code JWT_SECRET}
 * @param vidaAccessToken  vida del access token (15 minutos)
 * @param vidaRefreshToken vida del refresh token y de la cookie que lo lleva (7 días)
 * @param cookieSegura     si la cookie del refresh token lleva {@code Secure}
 */
@ConfigurationProperties("helpdesk.jwt")
public record PropiedadesJwt(String secreto, Duration vidaAccessToken, Duration vidaRefreshToken,
        boolean cookieSegura) {

    /** No muestra el secreto si el record termina en un log o en un mensaje de error. */
    @Override
    public String toString() {
        return "PropiedadesJwt[secreto=***, vidaAccessToken=" + vidaAccessToken
                + ", vidaRefreshToken=" + vidaRefreshToken + ", cookieSegura=" + cookieSegura + "]";
    }
}
