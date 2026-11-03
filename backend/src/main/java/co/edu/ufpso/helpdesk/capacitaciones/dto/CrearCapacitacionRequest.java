package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.Instant;

import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/capacitaciones} (HU33): capacitación masiva o personalizada sin solicitud
 * previa. La que nace de una solicitud se programa con {@code PUT /api/solicitudes-capacitacion/{id}/programar}.
 *
 * @param empresaId   obligatorio en una PERSONALIZADA; una MASIVA no lo lleva
 * @param fechaInicio fecha y hora, con zona (por ejemplo {@code 2026-11-20T09:00:00-05:00}). Debe ser
 *                    posterior a la hora actual
 */
public record CrearCapacitacionRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 200, message = "El título admite como máximo 200 caracteres.")
        String titulo,

        @NotNull(message = "El tipo es obligatorio.")
        TipoCapacitacion tipo,

        @NotNull(message = "El módulo es obligatorio.")
        ModuloErp modulo,

        @Pattern(regexp = Ids.PATRON, message = "El id de la empresa no es válido.")
        String empresaId,

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
