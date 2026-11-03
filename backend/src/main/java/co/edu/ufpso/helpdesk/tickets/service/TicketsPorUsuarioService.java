package co.edu.ufpso.helpdesk.tickets.service;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.tickets.domain.Ticket;

/**
 * Tickets que impiden borrar un usuario (HU08): los que creó, los que pidió como solicitante y los
 * que tiene asignados. MongoDB resuelve el {@code $or} con un índice por rama:
 * {@code ix_tickets_creador}, {@code ix_tickets_solicitante_estado_fecha} e
 * {@code ix_tickets_agente_estado_fecha}. Sin dependencias de otros módulos.
 */
@Service
public class TicketsPorUsuarioService {

    private final MongoTemplate template;

    public TicketsPorUsuarioService(MongoTemplate template) {
        this.template = template;
    }

    public long contar(ObjectId usuarioId) {
        Query consulta = new Query(new Criteria().orOperator(
                Criteria.where("creadorId").is(usuarioId),
                Criteria.where("solicitanteId").is(usuarioId),
                Criteria.where("agenteId").is(usuarioId)));
        return template.count(consulta, Ticket.class);
    }
}
