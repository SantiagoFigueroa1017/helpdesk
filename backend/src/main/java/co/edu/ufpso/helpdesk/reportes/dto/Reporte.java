package co.edu.ufpso.helpdesk.reportes.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respuesta común de los reportes A1 a A9 (tarea 2.9).
 *
 * @param reporte    código del reporte: {@code A1} a {@code A9}
 * @param titulo     nombre del reporte, para el panel de analítica
 * @param generadoEn hora en que se corrió el pipeline
 * @param parametros los parámetros que se aplicaron, ya con sus valores por defecto
 * @param resumen    totales o datos del reporte completo; null si el reporte no tiene
 * @param filas      lista de filas, o {@code Pagina} de filas en A4 y A6
 */
public record Reporte<T>(String reporte, String titulo, Instant generadoEn, Map<String, Object> parametros,
        Map<String, Object> resumen, T filas) {

    public Reporte {
        parametros = parametros == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parametros));
        resumen = resumen == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(resumen));
    }
}
