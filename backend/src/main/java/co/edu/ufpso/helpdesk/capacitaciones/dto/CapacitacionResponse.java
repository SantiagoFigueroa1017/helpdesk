package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.Instant;

/**
 * Una capacitación en las respuestas de la API (HU34).
 *
 * @param estado           estado guardado: PROGRAMADA o CANCELADA
 * @param estadoVisible    PROGRAMADA (todavía no empieza), EN_CURSO, REALIZADA (ya terminó) o CANCELADA
 * @param cuposDisponibles {@code cupo - cuposOcupados} (HU34)
 * @param inscritos        inscripciones que se perderían al eliminarla (HU36). Solo en el detalle; null en el listado
 * @param miInscripcionId  inscripción del usuario de la petición en esta capacitación, o null. Solo para
 *                         quien tiene {@code INSCRIPCIONES_PROPIAS}; el frontend decide si muestra
 *                         "Inscribirme" o "Cancelar inscripción"
 */
public record CapacitacionResponse(String id, String titulo, String tipo, String modulo, String empresaId,
        String empresaNombre, Instant fechaInicio, int duracionMinutos, Instant fechaFin, int cupo, int cuposOcupados,
        int cuposDisponibles, String instructorId, String instructorNombre, String estado, String estadoVisible,
        String solicitudId, Long inscritos, String miInscripcionId, Instant fechaCreacion,
        Instant fechaActualizacion) {
}
