package co.edu.ufpso.helpdesk.tickets.dto;

import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/tickets/{id}/estado} (HU23). Cualquier estado distinto del actual. */
public record CambiarEstadoRequest(
        @NotNull(message = "El estado es obligatorio.")
        EstadoTicket estado) {
}
