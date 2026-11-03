package co.edu.ufpso.helpdesk.capacitaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /api/solicitudes-capacitacion/{id}/rechazar} (HU33: rechazar indicando el motivo). */
public record RechazarSolicitudRequest(
        @NotBlank(message = "Indique el motivo del rechazo.")
        @Size(max = 1000, message = "El motivo admite como máximo 1000 caracteres.")
        String motivo) {
}
