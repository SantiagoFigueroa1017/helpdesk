package co.edu.ufpso.helpdesk.tickets.dto;

import java.time.Instant;

/**
 * Un ticket en el listado (HU22). No trae descripción, {@code detalle} ni historial: la consulta los
 * deja fuera con una proyección, porque un log de la API puede tener 20.000 caracteres y una página
 * llega a 100 tickets. El detalle completo sale en {@code GET /api/tickets/{id}}.
 */
public record TicketResumenResponse(String id, String empresaId, String empresaNombre, String solicitanteId,
        String solicitanteNombre, String agenteId, String agenteNombre, String modulo, String tipo, String titulo,
        String estado, Instant fechaLimiteRespuesta, Instant fechaResolucion, Instant fechaCreacion) {
}
