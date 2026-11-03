package co.edu.ufpso.helpdesk.tickets.repository;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.tickets.domain.ComentarioTicket;

/**
 * Acceso a {@code comentariosTicket}. Solo lo usan los servicios de tickets (regla entre módulos de
 * 2.2). Las dos consultas van por {@code ix_comentarios_ticket_fecha} ({@code ticketId},
 * {@code fechaCreacion}).
 */
public interface ComentarioTicketRepository extends MongoRepository<ComentarioTicket, ObjectId> {

    /** Conversación de un ticket, paginada. El orden lo pone el servicio. */
    Page<ComentarioTicket> findByTicketId(ObjectId ticketId, Pageable pageable);

    /** HU24: borra la conversación dentro de la transacción del borrado del ticket. */
    long deleteByTicketId(ObjectId ticketId);
}
