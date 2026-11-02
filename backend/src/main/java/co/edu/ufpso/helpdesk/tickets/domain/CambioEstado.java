package co.edu.ufpso.helpdesk.tickets.domain;

import java.time.Instant;

import org.bson.types.ObjectId;

/**
 * Una entrada de {@code historialEstados}, embebida en el ticket (tope 50, HU23). La primera se
 * escribe al crear el ticket, con estado ABIERTO.
 *
 * @param estado    estado al que pasó el ticket
 * @param fecha     cuándo
 * @param usuarioId quién hizo el cambio
 */
public record CambioEstado(EstadoTicket estado, Instant fecha, ObjectId usuarioId) {
}
