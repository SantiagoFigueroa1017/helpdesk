package co.edu.ufpso.helpdesk.planes.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.planes.domain.Plan;

/**
 * Acceso a {@code planes}. Solo lo usa el módulo de planes (regla entre módulos de 2.2). El listado
 * con filtros opcionales se arma con {@code MongoTemplate} en {@code PlanService}.
 */
public interface PlanRepository extends MongoRepository<Plan, ObjectId> {

    /** HU09: nombre único. Usa {@code ux_planes_nombre}. */
    boolean existsByNombre(String nombre);

    /** HU11: nombre único entre los demás planes. Usa {@code ux_planes_nombre}. */
    boolean existsByNombreAndIdNot(String nombre, ObjectId id);
}
