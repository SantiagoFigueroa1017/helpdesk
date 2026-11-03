package co.edu.ufpso.helpdesk.reportes.dto;

/**
 * Fila de A9: incidentes de los asistentes de un módulo antes y después de su capacitación.
 *
 * @param asistencias         inscripciones con {@code asistio: true} que entraron al reporte
 * @param antes               incidentes del mismo usuario y módulo en la ventana anterior a {@code fechaFin}
 * @param despues             incidentes en la ventana posterior
 * @param variacionPorcentual (despues - antes) / antes × 100; null si antes = 0
 */
public record FilaImpactoCapacitacion(String modulo, int asistencias, int antes, int despues,
        Double variacionPorcentual) {
}
