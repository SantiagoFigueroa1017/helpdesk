package co.edu.ufpso.helpdesk.capacitaciones.service;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;

/**
 * Lo que el borrado de un usuario (HU08) necesita del módulo de capacitaciones. Sin dependencias de
 * otros módulos.
 */
@Service
public class CapacitacionesPorUsuarioService {

    private final MongoTemplate template;

    public CapacitacionesPorUsuarioService(MongoTemplate template) {
        this.template = template;
    }

    /**
     * Capacitaciones PROGRAMADAS donde el usuario es instructor y que no han terminado (HU08 v.4):
     * bloquean el borrado. Usa {@code ix_capacitaciones_instructor_fecha}.
     */
    public long contarFuturasComoInstructor(ObjectId usuarioId, Instant ahora) {
        Query consulta = new Query(Criteria.where("instructorId").is(usuarioId)
                .and("estado").is(EstadoCapacitacion.PROGRAMADA)
                .and("fechaFin").gt(ahora));
        return template.count(consulta, Capacitacion.class);
    }

    /**
     * Cascada de HU08: libera el cupo de cada capacitación del usuario que todavía no termina y borra
     * todas sus inscripciones. Debe correr dentro de la transacción del borrado. Las inscripciones
     * se leen con {@code ix_inscripciones_usuario_fecha}.
     *
     * @return cuántas inscripciones borró
     */
    public long liberarInscripciones(ObjectId usuarioId, Instant ahora) {
        Query delUsuario = new Query(Criteria.where("usuarioId").is(usuarioId));
        List<ObjectId> capacitacionIds = template.findDistinct(delUsuario, "capacitacionId", Inscripcion.class,
                ObjectId.class);
        if (!capacitacionIds.isEmpty()) {
            // Un usuario tiene una sola inscripción por capacitación (ux_inscripciones_capacitacion_usuario),
            // así que basta restar 1 a cada una. cuposOcupados > 0 evita un contador negativo.
            template.updateMulti(new Query(Criteria.where("_id").in(capacitacionIds)
                            .and("fechaFin").gt(ahora)
                            .and("cuposOcupados").gt(0)),
                    new Update().inc("cuposOcupados", -1), Capacitacion.class);
        }
        return template.remove(delUsuario, Inscripcion.class).getDeletedCount();
    }
}
