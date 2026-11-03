package co.edu.ufpso.helpdesk.tickets.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.tickets.domain.Ticket;

/**
 * Qué tickets existen, para vincularlos a un artículo de la base de conocimientos (HU29 a HU31, tarea 2.7).
 * Sin dependencias de otros módulos, igual que los demás servicios pequeños de 2.5 y 2.6.
 */
@Service
public class TicketsExistentesService {

    private final MongoTemplate template;

    public TicketsExistentesService(MongoTemplate template) {
        this.template = template;
    }

    /** Los ids de la lista que existen. Una consulta por {@code _id} con {@code $in}, solo trae el {@code _id}. */
    public Set<ObjectId> existentes(Collection<ObjectId> ids) {
        Set<ObjectId> unicos = new HashSet<>(ids);
        unicos.removeIf(Objects::isNull);
        Set<ObjectId> encontrados = new HashSet<>();
        if (unicos.isEmpty()) {
            return encontrados;
        }
        Query consulta = new Query(Criteria.where("_id").in(unicos));
        consulta.fields().include("_id");
        for (Ticket t : template.find(consulta, Ticket.class)) {
            encontrados.add(t.getId());
        }
        return encontrados;
    }

    public boolean existe(ObjectId id) {
        return template.exists(new Query(Criteria.where("_id").is(id)), Ticket.class);
    }
}
