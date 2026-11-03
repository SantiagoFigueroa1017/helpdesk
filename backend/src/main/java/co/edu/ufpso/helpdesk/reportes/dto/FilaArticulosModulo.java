package co.edu.ufpso.helpdesk.reportes.dto;

/**
 * Fila de A7: artículos de un módulo y los tickets que resolvieron.
 *
 * @param conVector         artículos con vector generado (salen en la búsqueda por significado)
 * @param ticketsVinculados tickets distintos vinculados a algún artículo del módulo
 * @param ticketsResueltos  de esos, los RESUELTO o CERRADO
 */
public record FilaArticulosModulo(String modulo, int articulos, int publicos, int internos, int conVector,
        int ticketsVinculados, int ticketsResueltos, double resueltosPorArticulo) {
}
