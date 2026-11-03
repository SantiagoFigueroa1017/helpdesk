package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.util.List;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/capacitaciones/{id}/asistencia} (HU39): toda la lista en una petición.
 * Todas las inscripciones deben ser de esa capacitación.
 */
public record MarcarAsistenciasRequest(
        @NotEmpty(message = "Envíe al menos una marca de asistencia.")
        @Size(max = 1000, message = "Se admiten como máximo 1000 marcas por petición.")
        List<@Valid @NotNull(message = "Una marca vino vacía.") Marca> marcas) {

    public record Marca(
            @NotNull(message = "El id de la inscripción es obligatorio.")
            @Pattern(regexp = Ids.PATRON, message = "El id de la inscripción no es válido.")
            String inscripcionId,

            @NotNull(message = "Indique si el usuario asistió (true) o no (false).")
            Boolean asistio) {
    }
}
