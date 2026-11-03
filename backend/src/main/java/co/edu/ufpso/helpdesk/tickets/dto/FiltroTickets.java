package co.edu.ufpso.helpdesk.tickets.dto;

import java.time.LocalDate;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;

/**
 * Filtros opcionales del listado de tickets (HU22). Todos se combinan con "y".
 *
 * @param desde      fecha de creación desde ese día, inclusive, en hora de Bogotá
 * @param hasta      fecha de creación hasta ese día, inclusive, en hora de Bogotá
 * @param empresaId  solo lo usa el personal de soporte; a un usuario de empresa se le fuerza la suya
 * @param agenteId   tickets asignados a ese agente
 * @param sinAsignar true: solo tickets sin agente. No se combina con {@code agenteId}
 */
public record FiltroTickets(EstadoTicket estado, ModuloErp modulo, TipoTicket tipo, LocalDate desde,
        LocalDate hasta, String empresaId, String agenteId, Boolean sinAsignar) {
}
