package co.edu.ufpso.helpdesk.conocimiento.service;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;

/**
 * Vínculo artículo-ticket visto desde el ticket (tarea 2.6). El vínculo vive solo en
 * {@code articulos.ticketIds} (modelo 1.2). Sin dependencias de otros módulos.
 */
@Service
public class ArticulosPorTicketService {

    private final MongoTemplate template;

    public ArticulosPorTicketService(MongoTemplate template) {
        this.template = template;
    }

    /**
     * HU24: quita el ticket de todos los artículos que lo citan como solución, con {@code $pull}.
     * Encuentra los artículos con {@code ix_articulos_ticketIds}. Si se llama dentro de una
     * transacción, participa en ella. No cambia {@code fechaActualizacion}: el contenido del artículo
     * sigue igual.
     *
     * @return cuántos artículos cambiaron
     */
    public long desvincular(ObjectId ticketId) {
        Query conElTicket = new Query(Criteria.where("ticketIds").is(ticketId));
        return template.updateMulti(conElTicket, new Update().pull("ticketIds", ticketId), Articulo.class)
                .getModifiedCount();
    }
}
