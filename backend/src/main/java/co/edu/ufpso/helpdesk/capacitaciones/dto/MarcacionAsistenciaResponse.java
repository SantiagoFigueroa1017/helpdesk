package co.edu.ufpso.helpdesk.capacitaciones.dto;

/**
 * Respuesta de {@code PUT /api/capacitaciones/{id}/asistencia}.
 *
 * @param marcadas inscripciones marcadas en la petición
 */
public record MarcacionAsistenciaResponse(int marcadas) {
}
