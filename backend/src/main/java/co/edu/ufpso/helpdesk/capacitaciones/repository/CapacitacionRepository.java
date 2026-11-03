package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;

/**
 * Acceso a {@code capacitaciones}. Solo lo usa el módulo de capacitaciones (regla entre módulos de
 * 2.2). El cronograma (2.8) lo arma {@code CapacitacionService} con {@code MongoTemplate}, apoyado en
 * {@code ix_capacitaciones_fecha_modulo} e {@code ix_capacitaciones_empresa_fecha}.
 */
public interface CapacitacionRepository extends MongoRepository<Capacitacion, ObjectId> {

    /** HU04: capacitaciones personalizadas de la empresa. Usa el prefijo de {@code ix_capacitaciones_empresa_fecha}. */
    long countByEmpresaId(ObjectId empresaId);
}
