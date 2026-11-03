/**
 * Reportes A1 a A9 con el Framework de Agregación, expuestos por la API (tarea 2.9).
 *
 * <p>{@code service.PipelinesReportes} arma cada pipeline con {@code Document} del driver, igual que los
 * scripts de {@code database/reportes/}; {@code service.ReportesService} los corre y {@code controller} los
 * publica en {@code /api/reportes/**} con {@code REPORTES_VER}.
 *
 * <p>Excepción a la regla de 2.2: un reporte cruza varias colecciones sin ser dueño de ninguna, así que lee las
 * colecciones por nombre con {@code MongoTemplate#getDb()}. No importa el {@code domain} ni el {@code repository}
 * de otro módulo. Otro módulo solo entra por el {@code service}.
 */
package co.edu.ufpso.helpdesk.reportes;
