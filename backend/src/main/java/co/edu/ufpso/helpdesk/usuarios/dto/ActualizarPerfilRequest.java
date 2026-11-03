package co.edu.ufpso.helpdesk.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/perfil} (HU07, sin permiso). El usuario cambia su nombre y, si quiere,
 * su contraseña: para eso envía la actual y la nueva. Cambiar la contraseña cierra todas sus
 * sesiones, también la actual.
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre admite como máximo 150 caracteres.")
        String nombre,

        String passwordActual,

        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String nuevaPassword) {

    @Override
    public String toString() {
        return "ActualizarPerfilRequest[nombre=" + nombre + ", cambiaPassword=" + (nuevaPassword != null) + "]";
    }
}
