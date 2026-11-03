package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;

/**
 * Acceso a {@code solicitudesCapacitacion}. Solo lo usa el módulo de capacitaciones (regla entre
 * módulos de 2.2). La bandeja del agente, las solicitudes de una empresa y el límite del plan (2.8)
 * los arma {@code SolicitudCapacitacionService} con {@code MongoTemplate}, apoyados en
 * {@code ix_solicitudes_estado_fecha} e {@code ix_solicitudes_empresa_fecha}.
 */
public interface SolicitudCapacitacionRepository extends MongoRepository<SolicitudCapacitacion, ObjectId> {

    /** HU04: solicitudes de la empresa. Usa el prefijo de {@code ix_solicitudes_empresa_fecha}. */
    long countByEmpresaId(ObjectId empresaId);
}
