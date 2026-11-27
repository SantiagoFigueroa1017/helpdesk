package co.edu.ufpso.helpdesk.tickets.domain;

/**
 * Tipo de una entrada de {@code historialEstados} (tarea 3.7). Una entrada sin tipo es un cambio de
 * estado, como desde 2.6; así los tickets ya guardados no cambian.
 */
public enum TipoEntradaHistorial {
    /** Se asignó, reasignó o quitó el agente. {@code agenteId} es el nuevo; null si quedó sin agente. */
    ASIGNACION
}
