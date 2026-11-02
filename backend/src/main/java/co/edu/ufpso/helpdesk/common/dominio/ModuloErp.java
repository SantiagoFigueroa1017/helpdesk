package co.edu.ufpso.helpdesk.common.dominio;

/**
 * Los 5 módulos del ERP/POS de Sistemas Integrados (contexto maestro, sección 3).
 *
 * <p>Lo usan tickets, membresias, articulos, capacitaciones y solicitudesCapacitacion. Vive en
 * {@code common} porque un módulo no puede usar el {@code domain} de otro (regla de 2.2). Los
 * nombres son exactamente los del enum de los validadores: Spring Data guarda el enum por su
 * nombre.
 */
public enum ModuloErp {
    VENTAS_ONLINE,
    INVENTARIOS,
    COMPRAS,
    PRODUCCION,
    VENTAS_EN_SITIO
}
