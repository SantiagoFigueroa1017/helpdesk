package co.edu.ufpso.helpdesk.reportes.dto;

import java.time.Instant;

/**
 * Fila de A6: asistencia de una capacitación.
 *
 * @param estado               PROGRAMADA o CANCELADA (el guardado)
 * @param realizada            PROGRAMADA y con {@code fechaFin} pasada
 * @param marcadas             inscripciones con la asistencia marcada (sí o no)
 * @param porcentajeAsistencia asistieron / marcadas × 100; null si no hay marcas. Sin marcar no cuenta como ausencia
 */
public record FilaAsistenciaCapacitacion(String capacitacionId, String titulo, String tipo, String modulo,
        Instant fechaInicio, String estado, boolean realizada, int cupo, int cuposOcupados, int inscritos,
        int asistieron, int marcadas, Double porcentajeAsistencia) {
}
