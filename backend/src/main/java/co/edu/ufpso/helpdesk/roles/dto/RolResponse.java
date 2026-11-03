package co.edu.ufpso.helpdesk.roles.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un rol en las respuestas de la API (HU26). Los permisos van en el orden del catálogo.
 *
 * @param usuariosAsignados cuántos usuarios lo tienen; se cuenta en {@code usuarios.rolIds}
 */
public record RolResponse(String id, String nombre, String descripcion, String alcance, List<String> permisos,
        boolean activo, boolean esSistema, long usuariosAsignados, Instant fechaCreacion,
        Instant fechaActualizacion) {
}
