package co.edu.ufpso.helpdesk.usuarios.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un usuario en las respuestas de la API (HU06). Nunca lleva la contraseña ni su hash.
 *
 * @param empresaId null si el usuario es de alcance GLOBAL
 * @param roles     id, nombre y alcance de cada rol; un rol que ya no existe no aparece
 */
public record UsuarioResponse(String id, String nombre, String email, String empresaId, List<RolResumen> roles,
        boolean activo, Instant fechaCreacion, Instant fechaActualizacion) {

    public record RolResumen(String id, String nombre, String alcance, boolean activo) {
    }
}
