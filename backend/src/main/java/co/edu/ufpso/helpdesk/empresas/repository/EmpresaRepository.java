package co.edu.ufpso.helpdesk.empresas.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.empresas.domain.Empresa;

/**
 * Acceso a {@code empresas}. Solo lo usa el servicio de empresas (regla entre módulos de 2.2).
 * Las consultas se agregan en 2.5 (HU01 a HU04), apoyadas en {@code ux_empresas_nit} e
 * {@code ix_empresas_razonSocial}.
 */
public interface EmpresaRepository extends MongoRepository<Empresa, ObjectId> {
}
