package co.edu.ufpso.helpdesk.auth.service;

import java.time.Instant;

import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Resultado de un login o de una renovación. El controlador pone {@code refreshToken} en la cookie
 * y el resto en el cuerpo de la respuesta.
 */
public record SesionEmitida(String accessToken, Instant accessTokenExpira, String refreshToken,
        Instant refreshTokenExpira, UsuarioAutenticado usuario) {

    /** Sin los tokens: {@code toString} puede terminar en un log. */
    @Override
    public String toString() {
        return "SesionEmitida[usuario=" + usuario.id() + ", accessTokenExpira=" + accessTokenExpira + "]";
    }
}
