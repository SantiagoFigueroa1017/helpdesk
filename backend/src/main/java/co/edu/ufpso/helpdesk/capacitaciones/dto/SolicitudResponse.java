package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Una solicitud de capacitación personalizada en las respuestas de la API.
 *
 * @param fechaPreferida  día preferido en hora de Bogotá, o null
 * @param capacitacionId  capacitación que la atendió; solo en PROGRAMADA
 * @param motivoRechazo   solo en RECHAZADA
 * @param atendidaPorId   quien la programó o la rechazó. Una PENDIENTE puede conservarlo si volvió a ese
 *                        estado porque se eliminó su capacitación (HU36 v.4)
 */
public record SolicitudResponse(String id, String empresaId, String empresaNombre, String solicitanteId,
        String solicitanteNombre, String modulo, String descripcion, int participantesEstimados,
        LocalDate fechaPreferida, String estado, String capacitacionId, String motivoRechazo, String atendidaPorId,
        String atendidaPorNombre, Instant fechaCreacion, Instant fechaActualizacion) {
}
