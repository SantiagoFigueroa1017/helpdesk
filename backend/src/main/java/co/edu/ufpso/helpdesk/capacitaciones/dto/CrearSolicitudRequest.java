package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.LocalDate;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/solicitudes-capacitacion} (HU33, solicitud del CLIENTE_ADMIN). No trae
 * empresa: siempre es la del usuario.
 *
 * @param fechaPreferida día preferido, opcional, no anterior a hoy (hora de Bogotá)
 */
public record CrearSolicitudRequest(
        @NotNull(message = "El módulo es obligatorio.")
        ModuloErp modulo,

        @NotBlank(message = "Describa qué necesita aprender el equipo.")
        @Size(max = 2000, message = "La descripción admite como máximo 2000 caracteres.")
        String descripcion,

        @NotNull(message = "Indique cuántos participantes estima.")
        @Min(value = 1, message = "Los participantes estimados deben ser al menos 1.")
        Integer participantesEstimados,

        LocalDate fechaPreferida) {
}
