package co.edu.ufpso.helpdesk.usuarios.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;
import co.edu.ufpso.helpdesk.usuarios.repository.UsuarioRepository;

/**
 * Cuántos usuarios tienen cada rol (acta 2.4). Lo usa el módulo de roles: HU26 muestra el
 * conteo, HU27 no deja cambiar el alcance de un rol con usuarios y HU28 no deja borrarlo. El conteo
 * se calcula en cada consulta; no se guarda en el rol (modelo 1.2, colección {@code roles}).
 */
@Service
public class UsuariosPorRolService {

    private final UsuarioRepository repositorio;
    private final MongoTemplate template;

    public UsuariosPorRolService(UsuarioRepository repositorio, MongoTemplate template) {
        this.repositorio = repositorio;
        this.template = template;
    }

    public long contarConRol(ObjectId rolId) {
        return repositorio.contarConRol(rolId);
    }

    /**
     * Conteo de varios roles en una sola consulta, para una página del listado (HU26). El primer
     * {@code $match} usa {@code ix_usuarios_rolIds}. Un rol sin usuarios no aparece en el mapa.
     */
    public Map<ObjectId, Long> contarPorRoles(Collection<ObjectId> rolIds) {
        Map<ObjectId, Long> conteos = new HashMap<>();
        if (rolIds.isEmpty()) {
            return conteos;
        }
        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("rolIds").in(rolIds)),
                Aggregation.project("rolIds"),
                Aggregation.unwind("rolIds"),
                Aggregation.match(Criteria.where("rolIds").in(rolIds)),
                Aggregation.group("rolIds").count().as("usuarios"));
        for (Document fila : template.aggregate(pipeline, Usuario.class, Document.class)) {
            conteos.put(fila.getObjectId("_id"), ((Number) fila.get("usuarios")).longValue());
        }
        return conteos;
    }
}
