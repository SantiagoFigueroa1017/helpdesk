package co.edu.ufpso.helpdesk.empresas.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.empresas.domain.Empresa;

/**
 * Acceso a {@code empresas}. Solo lo usa el módulo de empresas (regla entre módulos de 2.2). El
 * listado con filtros se arma con {@code MongoTemplate} en {@code EmpresaService}.
 */
public interface EmpresaRepository extends MongoRepository<Empresa, ObjectId> {

    /** HU01: NIT único. Usa {@code ux_empresas_nit}. */
    boolean existsByNit(String nit);

    /** HU03: NIT único entre las demás empresas. Usa {@code ux_empresas_nit}. */
    boolean existsByNitAndIdNot(String nit, ObjectId id);
}
