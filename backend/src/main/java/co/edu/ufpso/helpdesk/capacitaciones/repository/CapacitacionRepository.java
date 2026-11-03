package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;

/**
 * Acceso a {@code capacitaciones}. Solo lo usa el servicio de capacitaciones (regla entre
 * módulos de 2.2). Las consultas (cronograma, personalizadas de una empresa, sesiones de un
 * instructor) se agregan en 2.8, apoyadas en {@code ix_capacitaciones_fecha_modulo},
 * {@code ix_capacitaciones_empresa_fecha} e {@code ix_capacitaciones_instructor_fecha}.
 */
public interface CapacitacionRepository extends MongoRepository<Capacitacion, ObjectId> {

    /** HU04: capacitaciones personalizadas de la empresa. Usa el prefijo de {@code ix_capacitaciones_empresa_fecha}. */
    long countByEmpresaId(ObjectId empresaId);
}
