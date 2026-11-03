package co.edu.ufpso.helpdesk.empresas.service;

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

import co.edu.ufpso.helpdesk.empresas.domain.Empresa;

/**
 * Razón social de varias empresas en una sola consulta (tarea 2.6), para los listados de otros
 * módulos. Sin dependencias de otros módulos.
 */
@Service
public class NombresEmpresaService {

    private final MongoTemplate template;

    public NombresEmpresaService(MongoTemplate template) {
        this.template = template;
    }

    /** {@code _id → razonSocial}. Busca por {@code _id} con {@code $in} y solo trae la razón social. */
    public Map<ObjectId, String> nombres(Collection<ObjectId> ids) {
        Set<ObjectId> unicos = new HashSet<>(ids);
        unicos.removeIf(Objects::isNull);
        Map<ObjectId, String> nombres = new HashMap<>();
        if (unicos.isEmpty()) {
            return nombres;
        }
        Query consulta = new Query(Criteria.where("_id").in(unicos));
        consulta.fields().include("razonSocial");
        for (Empresa e : template.find(consulta, Empresa.class)) {
            nombres.put(e.getId(), e.getRazonSocial());
        }
        return nombres;
    }
}
