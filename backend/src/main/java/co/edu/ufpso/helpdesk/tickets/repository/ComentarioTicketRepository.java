package co.edu.ufpso.helpdesk.tickets.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.tickets.domain.ComentarioTicket;

/**
 * Acceso a {@code comentariosTicket}. Solo lo usa el servicio de tickets (regla entre módulos de
 * 2.2). Las consultas (conversación de un ticket, borrado en cascada de HU24) se agregan en 2.6,
 * apoyadas en {@code ix_comentarios_ticket_fecha}.
 */
public interface ComentarioTicketRepository extends MongoRepository<ComentarioTicket, ObjectId> {
}
