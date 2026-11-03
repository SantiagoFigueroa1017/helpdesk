package co.edu.ufpso.helpdesk.tickets.dto;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code PUT /api/tickets/{id}/asignacion} (HU23).
 *
 * @param agenteId agente que queda a cargo. null quita el agente, y solo lo puede hacer quien
 *                 tiene {@code TICKETS_ASIGNAR_OTROS}
 */
public record AsignarTicketRequest(
        @Pattern(regexp = Ids.PATRON, message = "El id del agente no es válido.")
        String agenteId) {
}
