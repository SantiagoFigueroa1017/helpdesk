package co.edu.ufpso.helpdesk.roles.dto;

import co.edu.ufpso.helpdesk.common.dominio.Permiso;

/** Un permiso del catálogo (HU26): código, grupo y descripción en español. */
public record PermisoResponse(String codigo, String grupo, String descripcion) {

    public static PermisoResponse de(Permiso p) {
        return new PermisoResponse(p.name(), p.getGrupo(), p.getDescripcion());
    }
}
