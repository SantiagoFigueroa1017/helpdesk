package co.edu.ufpso.helpdesk.tickets.dto;

/**
 * Respuesta de {@code DELETE /api/tickets/{id}} (HU24): lo que se borró en la misma transacción.
 *
 * @param comentariosEliminados  comentarios del ticket que se borraron
 * @param articulosDesvinculados artículos de la base de conocimientos de los que se quitó el ticket
 */
public record EliminacionTicketResponse(long comentariosEliminados, long articulosDesvinculados) {
}
