package co.edu.ufpso.helpdesk.security;

import java.util.Set;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;

/**
 * El usuario de la petición, con sus permisos efectivos (contexto 8.4, acta 2.4). Es el principal
 * de la autenticación: los controladores lo reciben con {@code @AuthenticationPrincipal}.
 *
 * <p>Los servicios filtran por {@link #empresaId()} cuando el alcance es EMPRESA, nunca por un id
 * que llegue en la petición (contexto 8.3, protección IDOR).
 *
 * @param empresaId null cuando el alcance es GLOBAL
 * @param permisos  unión de los permisos de los roles activos del usuario
 */
public record UsuarioAutenticado(ObjectId id, String nombre, String email, ObjectId empresaId,
        AlcanceRol alcance, Set<Permiso> permisos) {

    public UsuarioAutenticado {
        permisos = Set.copyOf(permisos);
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }

    public boolean esDeEmpresa() {
        return alcance == AlcanceRol.EMPRESA;
    }
}
