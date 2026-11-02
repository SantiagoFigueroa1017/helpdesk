package co.edu.ufpso.helpdesk.tickets.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.tickets.domain.Ticket;

/**
 * Acceso a {@code tickets}. Solo lo usa {@code TicketService} (regla entre módulos de 2.2).
 *
 * <p>Las consultas de listado (HU22), bandejas y bloqueos de borrado se agregan en 2.6, cada una
 * apoyada en su índice de {@code database/indices/00_indices.js}. Los índices no se crean desde
 * aquí ({@code auto-index-creation: false}).
 */
public interface TicketRepository extends MongoRepository<Ticket, ObjectId> {
}
