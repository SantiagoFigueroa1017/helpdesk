package co.edu.ufpso.helpdesk.usuarios.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Nombres de varios usuarios en una sola consulta (tarea 2.6). Lo usan los listados y detalles de
 * otros módulos para mostrar "quién" sin una consulta por fila. Sin dependencias de otros módulos.
 *
 * <p>Un id que ya no existe no aparece en el mapa: quien llama muestra "Usuario eliminado"
 * (modelo 1.2, referencias que quedan sin documento).
 */
@Service
public class NombresUsuarioService {

    private final MongoTemplate template;

    public NombresUsuarioService(MongoTemplate template) {
        this.template = template;
    }

    /** {@code _id → nombre}. Busca por {@code _id} con {@code $in} y solo trae el nombre. */
    public Map<ObjectId, String> nombres(Collection<ObjectId> ids) {
        Set<ObjectId> unicos = new HashSet<>(ids);
        unicos.removeIf(Objects::isNull);
        Map<ObjectId, String> nombres = new HashMap<>();
        if (unicos.isEmpty()) {
            return nombres;
        }
        Query consulta = new Query(Criteria.where("_id").in(unicos));
        consulta.fields().include("nombre");
        for (Usuario u : template.find(consulta, Usuario.class)) {
            nombres.put(u.getId(), u.getNombre());
        }
        return nombres;
    }
}
