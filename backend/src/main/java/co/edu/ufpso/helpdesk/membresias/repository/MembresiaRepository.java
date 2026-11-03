package co.edu.ufpso.helpdesk.membresias.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.membresias.domain.Membresia;

/**
 * Acceso a {@code membresias}. Solo lo usa el servicio de membresías (regla entre módulos de 2.2).
 * Las consultas se agregan en 2.5 (HU13 a HU16 y verificación de soporte), apoyadas en
 * {@code ux_membresias_empresa} e {@code ix_membresias_plan_estado}.
 */
public interface MembresiaRepository extends MongoRepository<Membresia, ObjectId> {
}
