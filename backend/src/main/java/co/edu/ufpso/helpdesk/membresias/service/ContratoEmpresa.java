package co.edu.ufpso.helpdesk.membresias.service;

import java.time.Instant;
import java.util.Set;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.planes.dto.PlanResponse;

/**
 * Lo que la empresa tiene contratado, para los módulos que reciben soporte (tarea 2.6).
 *
 * <p>Tickets lo usa al crear (HU21): el módulo del ticket debe estar en {@code modulos} y la fecha
 * límite de respuesta sale de {@code plan.limites().horasRespuesta()}. Capacitaciones (2.8) lo usa
 * para el módulo de una personalizada y para el límite {@code capacitacionesIncluidas}, que se cuenta
 * en el mes pagado que termina en {@code fechaVencimiento}.
 *
 * @param modulos          módulos contratados en la membresía
 * @param plan             plan de la membresía, con sus límites
 * @param fechaInicio      inicio de la membresía
 * @param fechaVencimiento hasta cuándo está pagada
 */
public record ContratoEmpresa(Set<ModuloErp> modulos, PlanResponse plan, Instant fechaInicio,
        Instant fechaVencimiento) {

    public ContratoEmpresa {
        modulos = Set.copyOf(modulos);
    }
}
