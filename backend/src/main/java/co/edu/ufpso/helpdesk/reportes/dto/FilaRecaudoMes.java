package co.edu.ufpso.helpdesk.reportes.dto;

import java.math.BigDecimal;

/**
 * Fila de A5: pagos de un mes en hora de Bogotá. Un mes sin pagos no aparece.
 *
 * @param mes                {@code AAAA-MM}
 * @param recaudo            suma de los pagos APROBADO
 * @param montoFallido       suma de los pagos FALLIDO
 * @param porcentajeFallidos fallidos / (aprobados + fallidos) × 100, con un decimal
 */
public record FilaRecaudoMes(String mes, BigDecimal recaudo, int aprobados, int fallidos, BigDecimal montoFallido,
        double porcentajeFallidos) {
}
