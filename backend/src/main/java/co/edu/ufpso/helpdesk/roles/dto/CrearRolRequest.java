package co.edu.ufpso.helpdesk.roles.dto;

import java.util.Set;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/roles} (HU25). El rol nace activo y nunca como rol del sistema: un
 * cliente no puede enviar {@code esSistema}. Un código que no está en el catálogo responde 400.
 */
public record CrearRolRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 50, message = "El nombre admite como máximo 50 caracteres.")
        String nombre,

        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 500, message = "La descripción admite como máximo 500 caracteres.")
        String descripcion,

        @NotNull(message = "El alcance es obligatorio: GLOBAL o EMPRESA.")
        AlcanceRol alcance,

        @NotEmpty(message = "El rol debe tener al menos un permiso.")
        Set<@NotNull(message = "Hay un permiso vacío en la lista.") Permiso> permisos) {
}
