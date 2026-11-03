package co.edu.ufpso.helpdesk.capacitaciones.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.capacitaciones.repository.CapacitacionRepository;
import co.edu.ufpso.helpdesk.capacitaciones.repository.SolicitudCapacitacionRepository;

/**
 * Capacitaciones personalizadas y solicitudes de una empresa (HU04 v.4). Las masivas llevan
 * {@code empresaId: null}, así que el conteo solo trae personalizadas. Sin dependencias de otros módulos.
 */
@Service
public class CapacitacionesPorEmpresaService {

    private final CapacitacionRepository capacitaciones;
    private final SolicitudCapacitacionRepository solicitudes;

    public CapacitacionesPorEmpresaService(CapacitacionRepository capacitaciones,
            SolicitudCapacitacionRepository solicitudes) {
        this.capacitaciones = capacitaciones;
        this.solicitudes = solicitudes;
    }

    public long contarPersonalizadas(ObjectId empresaId) {
        return capacitaciones.countByEmpresaId(empresaId);
    }

    public long contarSolicitudes(ObjectId empresaId) {
        return solicitudes.countByEmpresaId(empresaId);
    }
}
