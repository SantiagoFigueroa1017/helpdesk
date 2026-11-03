package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;

/**
 * Acceso a {@code inscripciones}. Solo lo usa el servicio de capacitaciones (regla entre
 * módulos de 2.2). Las consultas (inscritos de una sesión, mis inscripciones) se agregan en 2.8,
 * apoyadas en {@code ux_inscripciones_capacitacion_usuario} e
 * {@code ix_inscripciones_usuario_fecha}.
 */
public interface InscripcionRepository extends MongoRepository<Inscripcion, ObjectId> {
}
