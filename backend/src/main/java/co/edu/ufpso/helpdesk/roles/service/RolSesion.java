package co.edu.ufpso.helpdesk.roles.service;

import java.util.Set;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;

/** Lo que la seguridad necesita de un rol para armar los permisos efectivos (acta 2.4). */
public record RolSesion(ObjectId id, String nombre, AlcanceRol alcance, boolean activo, Set<Permiso> permisos) {

    public RolSesion {
        permisos = Set.copyOf(permisos);
    }
}
