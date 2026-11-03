package co.edu.ufpso.helpdesk.membresias.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;
import co.edu.ufpso.helpdesk.membresias.repository.PagoRepository;

/**
 * Membresía y pagos de una empresa (HU04). Sin dependencias de otros módulos, igual que
 * {@link MembresiasPorPlanService}.
 */
@Service
public class MembresiasPorEmpresaService {

    private final MembresiaRepository membresias;
    private final PagoRepository pagos;

    public MembresiasPorEmpresaService(MembresiaRepository membresias, PagoRepository pagos) {
        this.membresias = membresias;
        this.pagos = pagos;
    }

    public long contarMembresias(ObjectId empresaId) {
        return membresias.countByEmpresaId(empresaId);
    }

    public long contarPagos(ObjectId empresaId) {
        return pagos.countByEmpresaId(empresaId);
    }
}
