package co.edu.ufpso.helpdesk.usuarios.dto;

import java.util.List;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/usuarios/{id}} (HU07). La empresa no cambia. {@code nuevaPassword} es
 * opcional: si llega, reemplaza la contraseña y cierra todas las sesiones del usuario.
 */
public record ActualizarUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre admite como máximo 150 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 254, message = "El correo admite como máximo 254 caracteres.")
        @Pattern(regexp = UsuarioDtos.PATRON_EMAIL, message = "El correo no es válido.")
        String email,

        @NotEmpty(message = "El usuario debe tener al menos un rol.")
        @Size(max = 10, message = "Un usuario admite como máximo 10 roles.")
        List<@NotBlank(message = "Hay un rol vacío en la lista.")
                @Pattern(regexp = Ids.PATRON, message = "Hay un id de rol que no es válido.") String> rolIds,

        @NotNull(message = "El estado es obligatorio.")
        Boolean activo,

        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String nuevaPassword) {

    @Override
    public String toString() {
        return "ActualizarUsuarioRequest[nombre=" + nombre + ", email=" + email + ", rolIds=" + rolIds + ", activo="
                + activo + ", cambiaPassword=" + (nuevaPassword != null) + "]";
    }
}
