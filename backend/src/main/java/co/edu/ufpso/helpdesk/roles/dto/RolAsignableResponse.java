package co.edu.ufpso.helpdesk.roles.dto;

import java.util.List;

/** Un rol que el usuario puede asignar al crear o editar usuarios (HU05, HU07). */
public record RolAsignableResponse(String id, String nombre, String descripcion, String alcance,
        List<String> permisos) {
}
