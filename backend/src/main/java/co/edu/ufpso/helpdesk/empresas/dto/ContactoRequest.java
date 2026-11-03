package co.edu.ufpso.helpdesk.empresas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contacto de la empresa (HU01, HU03). Mismas reglas que el validador de {@code empresas.contacto}.
 * {@code direccion} y {@code ciudad} son opcionales: si llegan vacías no se guardan.
 */
public record ContactoRequest(
        @NotBlank(message = "El nombre del contacto es obligatorio.")
        @Size(max = 150, message = "El nombre del contacto admite como máximo 150 caracteres.")
        String nombre,

        @NotBlank(message = "El correo del contacto es obligatorio.")
        @Size(max = 254, message = "El correo del contacto admite como máximo 254 caracteres.")
        @Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$", message = "El correo del contacto no es válido.")
        String email,

        @NotBlank(message = "El teléfono del contacto es obligatorio.")
        @Pattern(regexp = "^[0-9+() -]{7,20}$",
                message = "El teléfono admite de 7 a 20 caracteres: dígitos, +, paréntesis, espacios y guiones.")
        String telefono,

        @Size(max = 200, message = "La dirección admite como máximo 200 caracteres.")
        String direccion,

        @Size(max = 100, message = "La ciudad admite como máximo 100 caracteres.")
        String ciudad) {
}
