package co.edu.ufpso.helpdesk.membresias.repository;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.membresias.domain.Pago;

/**
 * Acceso a {@code pagos}. Solo lo usa el módulo de membresías (regla entre módulos de 2.2). El
 * listado con filtros se arma con {@code MongoTemplate} en {@code PagoService}.
 */
public interface PagoRepository extends MongoRepository<Pago, ObjectId> {

    /** HU04: pagos de la empresa. Usa el prefijo de {@code ix_pagos_empresa_fecha}. */
    long countByEmpresaId(ObjectId empresaId);

    /** HU16: pagos que bloquean el borrado de la membresía. Usa {@code ix_pagos_membresia}. */
    long countByMembresiaId(ObjectId membresiaId);

    /**
     * HU19: si la membresía tiene otro pago posterior a {@code fechaPago}. Si no lo tiene, el pago
     * editado es el más reciente y su cambio de resultado afecta a la membresía. Usa
     * {@code ix_pagos_membresia}.
     */
    boolean existsByMembresiaIdAndFechaPagoAfterAndIdNot(ObjectId membresiaId, Instant fechaPago, ObjectId id);
}
