package co.edu.ufpso.helpdesk.auth.dto;

import java.util.List;

import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Datos del usuario que el frontend usa para armar el menú y las rutas (contexto 9: se decide por
 * permisos, no por nombre de rol).
 *
 * @param empresaId null para el personal de Sistemas Integrados
 * @param permisos  códigos en orden alfabético
 */
public record UsuarioSesionResponse(String id, String nombre, String email, String alcance,
        String empresaId, List<String> permisos) {

    public static UsuarioSesionResponse de(UsuarioAutenticado u) {
        return new UsuarioSesionResponse(
                u.id().toHexString(),
                u.nombre(),
                u.email(),
                u.alcance().name(),
                u.empresaId() == null ? null : u.empresaId().toHexString(),
                u.permisos().stream().map(Enum::name).sorted().toList());
    }
}
