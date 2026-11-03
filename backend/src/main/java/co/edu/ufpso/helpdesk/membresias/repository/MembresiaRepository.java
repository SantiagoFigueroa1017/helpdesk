package co.edu.ufpso.helpdesk.membresias.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import co.edu.ufpso.helpdesk.membresias.domain.Membresia;

/**
 * Acceso a {@code membresias}. Solo lo usa el módulo de membresías (regla entre módulos de 2.2).
 * Consultas apoyadas en {@code ux_membresias_empresa} e {@code ix_membresias_plan_estado}.
 */
public interface MembresiaRepository extends MongoRepository<Membresia, ObjectId> {

    /** HU12: membresías que bloquean el borrado de un plan. Usa el prefijo de {@code ix_membresias_plan_estado}. */
    long countByPlanId(ObjectId planId);

    /** HU04: membresía de la empresa (0 o 1). Usa {@code ux_membresias_empresa}. */
    long countByEmpresaId(ObjectId empresaId);

    /** HU13: una membresía por empresa. Usa {@code ux_membresias_empresa}. */
    boolean existsByEmpresaId(ObjectId empresaId);

    /**
     * Verificación central de soporte (contexto 7.5): solo trae {@code estado} y
     * {@code fechaVencimiento}. Usa {@code ux_membresias_empresa}. El documento que devuelve está
     * incompleto: sirve para {@code motivoSinSoporte} y nada más.
     */
    @Query(value = "{ 'empresaId': ?0 }", fields = "{ 'estado': 1, 'fechaVencimiento': 1 }")
    Optional<Membresia> buscarParaSoporte(ObjectId empresaId);

    /** HU05 y HU07: plan de la empresa para {@code limites.maxUsuarios}. Usa {@code ux_membresias_empresa}. */
    Optional<Membresia> findByEmpresaId(ObjectId empresaId);
}
