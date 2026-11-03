package co.edu.ufpso.helpdesk.capacitaciones.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;

/**
 * Acceso a {@code solicitudesCapacitacion}. Solo lo usa el servicio de capacitaciones (regla
 * entre módulos de 2.2). Las consultas (bandeja del agente, solicitudes de una empresa y límite
 * del plan) se agregan en 2.8, apoyadas en {@code ix_solicitudes_estado_fecha} e
 * {@code ix_solicitudes_empresa_fecha}.
 */
public interface SolicitudCapacitacionRepository extends MongoRepository<SolicitudCapacitacion, ObjectId> {
}
