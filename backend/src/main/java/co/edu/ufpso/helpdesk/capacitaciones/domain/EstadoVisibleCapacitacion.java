package co.edu.ufpso.helpdesk.capacitaciones.domain;

/**
 * Estado que ve el usuario (HU34). Solo PROGRAMADA y CANCELADA se guardan; EN_CURSO y REALIZADA se
 * derivan de la hora actual con {@link Capacitacion#estadoVisible}.
 */
public enum EstadoVisibleCapacitacion {
    /** Programada y todavía no empieza. */
    PROGRAMADA,
    /** Programada, ya empezó y no ha terminado. */
    EN_CURSO,
    /** Programada y ya terminó ({@code fechaFin} pasada). */
    REALIZADA,
    CANCELADA
}
