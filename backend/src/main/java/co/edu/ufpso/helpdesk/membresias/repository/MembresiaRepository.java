package co.edu.ufpso.helpdesk.membresias.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.membresias.domain.Membresia;

/**
 * Acceso a {@code membresias}. Solo lo usa el módulo de membresías (regla entre módulos de 2.2).
 * Consultas apoyadas en {@code ux_membresias_empresa} e {@code ix_membresias_plan_estado}.
 */
public interface MembresiaRepository extends MongoRepository<Membresia, ObjectId> {

    /** HU12: membresías que bloquean el borrado de un plan. Usa el prefijo de {@code ix_membresias_plan_estado}. */
    long countByPlanId(ObjectId planId);
}
