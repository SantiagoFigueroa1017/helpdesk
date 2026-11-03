package co.edu.ufpso.helpdesk.capacitaciones.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/inscripciones/{id}/asistencia} (HU39). */
public record MarcarAsistenciaRequest(
        @NotNull(message = "Indique si el usuario asistió (true) o no (false).")
        Boolean asistio) {
}
