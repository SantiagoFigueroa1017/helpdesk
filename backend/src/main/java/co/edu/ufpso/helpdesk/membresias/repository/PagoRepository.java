package co.edu.ufpso.helpdesk.membresias.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.membresias.domain.Pago;

/**
 * Acceso a {@code pagos}. Solo lo usa el servicio de membresías (regla entre módulos de 2.2).
 * Las consultas se agregan en 2.5 (HU17 a HU20), apoyadas en {@code ix_pagos_empresa_fecha},
 * {@code ix_pagos_resultado_fecha} e {@code ix_pagos_membresia}.
 */
public interface PagoRepository extends MongoRepository<Pago, ObjectId> {

    /** HU04: pagos de la empresa. Usa el prefijo de {@code ix_pagos_empresa_fecha}. */
    long countByEmpresaId(ObjectId empresaId);
}
