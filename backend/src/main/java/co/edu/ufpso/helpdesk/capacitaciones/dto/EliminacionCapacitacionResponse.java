package co.edu.ufpso.helpdesk.capacitaciones.dto;

/**
 * Respuesta de {@code DELETE /api/capacitaciones/{id}} (HU36): lo que se borró en la misma transacción.
 *
 * @param inscripcionesEliminadas inscripciones que se borraron con la capacitación
 * @param solicitudDevuelta       true si la capacitación nació de una solicitud y esa solicitud volvió a
 *                                PENDIENTE (HU36 v.4)
 */
public record EliminacionCapacitacionResponse(long inscripcionesEliminadas, boolean solicitudDevuelta) {
}
