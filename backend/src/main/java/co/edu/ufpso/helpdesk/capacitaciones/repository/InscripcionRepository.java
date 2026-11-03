package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;

/**
 * Acceso a {@code inscripciones}. Solo lo usa el módulo de capacitaciones (regla entre módulos de
 * 2.2). Los listados (inscritos de una sesión, mis inscripciones) los arma {@code InscripcionService}
 * con {@code MongoTemplate}, apoyados en {@code ux_inscripciones_capacitacion_usuario} e
 * {@code ix_inscripciones_usuario_fecha}.
 */
public interface InscripcionRepository extends MongoRepository<Inscripcion, ObjectId> {

    /** HU37: ¿ya está inscrito? Usa {@code ux_inscripciones_capacitacion_usuario}. */
    boolean existsByCapacitacionIdAndUsuarioId(ObjectId capacitacionId, ObjectId usuarioId);
}
