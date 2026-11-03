package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.Instant;

/**
 * Una inscripción en las respuestas de la API (HU38): la capacitación, su fecha y si el usuario asistió.
 *
 * @param capacitacionEstadoVisible PROGRAMADA, EN_CURSO, REALIZADA o CANCELADA (como en HU34)
 * @param asistio                   null mientras no se marque (HU39)
 */
public record InscripcionResponse(String id, String capacitacionId, String capacitacionTitulo,
        String capacitacionModulo, Instant capacitacionFechaInicio, Instant capacitacionFechaFin,
        String capacitacionEstadoVisible, String usuarioId, String usuarioNombre, String empresaId,
        String empresaNombre, Boolean asistio, String marcadoPorId, String marcadoPorNombre, Instant fechaMarcado,
        Instant fechaCreacion) {
}
