package co.edu.ufpso.helpdesk.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo admite como máximo 254 caracteres.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 72, message = "La contraseña admite como máximo 72 caracteres.")
        String password) {

    /** Sin la contraseña: {@code toString} puede terminar en un log. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
