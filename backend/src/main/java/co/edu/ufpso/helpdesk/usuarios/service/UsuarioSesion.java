package co.edu.ufpso.helpdesk.usuarios.service;

import java.util.List;

import org.bson.types.ObjectId;

/**
 * Lo que la seguridad necesita de un usuario para armar su sesión (acta 2.4). Nunca lleva el hash
 * de la contraseña, porque se guarda en la caché {@code usuariosSesion}.
 *
 * @param empresaId null para el personal de Sistemas Integrados (roles GLOBAL)
 */
public record UsuarioSesion(ObjectId id, String nombre, String email, ObjectId empresaId,
        List<ObjectId> rolIds, boolean activo) {

    public UsuarioSesion {
        rolIds = List.copyOf(rolIds);
    }
}
