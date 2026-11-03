package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.Instant;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/solicitudes-capacitacion/{id}/programar} (HU33). El tipo es PERSONALIZADA,
 * y la empresa y el módulo salen de la solicitud.
 */
public record ProgramarSolicitudRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 200, message = "El título admite como máximo 200 caracteres.")
        String titulo,

        @NotNull(message = "La fecha y hora de inicio son obligatorias.")
        Instant fechaInicio,

        @NotNull(message = "La duración es obligatoria.")
        @Min(value = 1, message = "La duración debe ser de al menos 1 minuto.")
        @Max(value = 1440, message = "La duración admite como máximo 1440 minutos (24 horas).")
        Integer duracionMinutos,

        @NotNull(message = "El cupo es obligatorio.")
        @Min(value = 1, message = "El cupo debe ser mayor que cero.")
        Integer cupo,

        @NotNull(message = "El instructor es obligatorio.")
        @Pattern(regexp = Ids.PATRON, message = "El id del instructor no es válido.")
        String instructorId) {
}
