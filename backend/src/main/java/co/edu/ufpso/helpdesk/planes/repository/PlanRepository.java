package co.edu.ufpso.helpdesk.planes.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.planes.domain.Plan;

/**
 * Acceso a {@code planes}. Solo lo usa el servicio de planes (regla entre módulos de 2.2).
 * Las consultas se agregan en 2.5 (HU09 a HU12), apoyadas en {@code ux_planes_nombre}.
 */
public interface PlanRepository extends MongoRepository<Plan, ObjectId> {
}
