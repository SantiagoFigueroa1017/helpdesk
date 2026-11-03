package co.edu.ufpso.helpdesk.auth.dto;

import java.time.Instant;

import co.edu.ufpso.helpdesk.auth.service.SesionEmitida;

/**
 * Respuesta de {@code /login} y {@code /refresh}. El refresh token no va aquí: viaja en la cookie
 * HttpOnly, que el JavaScript de la página no puede leer (acta 2.4).
 *
 * @param expiraEn cuándo vence el access token; el frontend puede renovarlo un poco antes
 */
public record SesionResponse(String accessToken, String tipo, Instant expiraEn, UsuarioSesionResponse usuario) {

    public static SesionResponse de(SesionEmitida sesion) {
        return new SesionResponse(sesion.accessToken(), "Bearer", sesion.accessTokenExpira(),
                UsuarioSesionResponse.de(sesion.usuario()));
    }

    /** Sin el token: {@code toString} puede terminar en un log. */
    @Override
    public String toString() {
        return "SesionResponse[usuario=" + usuario.id() + ", expiraEn=" + expiraEn + "]";
    }
}
