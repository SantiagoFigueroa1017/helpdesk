package co.edu.ufpso.helpdesk.capacitaciones.dto;

import java.time.LocalDate;

import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Filtros opcionales del cronograma (HU34). Todos se combinan con "y".
 *
 * @param desde     fecha de inicio desde ese día, inclusive, en hora de Bogotá
 * @param hasta     fecha de inicio hasta ese día, inclusive, en hora de Bogotá
 * @param empresaId solo lo usa el personal de soporte; un usuario de empresa siempre ve las masivas y
 *                  las personalizadas de la suya
 */
public record FiltroCapacitaciones(ModuloErp modulo, TipoCapacitacion tipo, EstadoCapacitacion estado,
        LocalDate desde, LocalDate hasta, String empresaId) {
}
