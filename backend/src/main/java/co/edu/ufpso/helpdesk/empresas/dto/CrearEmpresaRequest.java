package co.edu.ufpso.helpdesk.empresas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/empresas} (HU01). La empresa nace sin membresía y por eso sin soporte. */
public record CrearEmpresaRequest(
        @NotBlank(message = "El NIT es obligatorio.")
        @Pattern(regexp = "^[0-9]{6,10}-[0-9]$",
                message = "El NIT debe tener de 6 a 10 dígitos, un guion y el dígito de verificación (900123456-1).")
        String nit,

        @NotBlank(message = "La razón social es obligatoria.")
        @Size(max = 200, message = "La razón social admite como máximo 200 caracteres.")
        String razonSocial,

        @NotNull(message = "El contacto es obligatorio.")
        @Valid
        ContactoRequest contacto) {
}
