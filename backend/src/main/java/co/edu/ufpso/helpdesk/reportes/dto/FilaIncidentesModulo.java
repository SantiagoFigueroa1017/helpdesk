package co.edu.ufpso.helpdesk.reportes.dto;

/**
 * Fila de A1: incidentes y tiempo promedio de resolución de un módulo del ERP.
 *
 * @param horasPromedioResolucion null si ningún incidente del módulo tiene {@code fechaResolucion}
 */
public record FilaIncidentesModulo(String modulo, int incidentes, int abiertos, int cerrados,
        Double horasPromedioResolucion, Double porcentajeCerrados) {
}
