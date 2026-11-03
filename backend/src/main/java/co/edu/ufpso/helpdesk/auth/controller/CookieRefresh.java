package co.edu.ufpso.helpdesk.auth.controller;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import co.edu.ufpso.helpdesk.security.PropiedadesJwt;

/**
 * Cookie que lleva el refresh token (acta 2.4).
 *
 * <ul>
 *   <li>{@code HttpOnly}: el JavaScript de la página no la puede leer, así que un XSS no se la lleva.</li>
 *   <li>{@code SameSite=Strict}: el navegador no la envía en peticiones que nacen en otro sitio (CSRF).</li>
 *   <li>{@code Path=/api/auth}: solo viaja a login, refresh y logout, no en cada petición de la API.</li>
 *   <li>{@code Secure} según {@code helpdesk.jwt.cookie-segura}; {@code Max-Age} igual a la vida del
 *       refresh token.</li>
 * </ul>
 */
@Component
public class CookieRefresh {

    public static final String NOMBRE = "helpdesk_refresh";
    static final String RUTA = "/api/auth";

    private final PropiedadesJwt propiedades;

    public CookieRefresh(PropiedadesJwt propiedades) {
        this.propiedades = propiedades;
    }

    /** Cookie con el token. Dura lo mismo que la sesión guardada en {@code refreshTokens}. */
    public ResponseCookie crear(String refreshToken) {
        return base(refreshToken).maxAge(propiedades.vidaRefreshToken()).build();
    }

    /** Cookie vacía con {@code Max-Age=0}: el navegador borra la que tenía. */
    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(propiedades.cookieSegura())
                .sameSite("Strict")
                .path(RUTA);
    }
}
