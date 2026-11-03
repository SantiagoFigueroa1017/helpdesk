package co.edu.ufpso.helpdesk.membresias.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;

/**
 * Cuántas membresías usan un plan (HU12). Lo usa el módulo de planes para bloquear el borrado.
 *
 * <p>Es un servicio aparte, sin dependencias de {@code planes}, porque el servicio de membresías
 * (HU13, HU15) sí consulta planes: si el servicio de planes dependiera de él, Spring no podría
 * crear los dos beans (dependencia circular). Mismo patrón que {@code UsuariosPorRolService}.
 */
@Service
public class MembresiasPorPlanService {

    private final MembresiaRepository repositorio;

    public MembresiasPorPlanService(MembresiaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public long contarConPlan(ObjectId planId) {
        return repositorio.countByPlanId(planId);
    }
}
