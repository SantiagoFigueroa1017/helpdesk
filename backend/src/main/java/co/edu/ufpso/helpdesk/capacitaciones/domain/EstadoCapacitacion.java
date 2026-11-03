package co.edu.ufpso.helpdesk.capacitaciones.domain;

/**
 * Estados guardados de la capacitación. "Realizada" no se guarda: se deriva de que
 * {@code fechaFin} ya pasó ({@link Capacitacion#realizada}).
 */
public enum EstadoCapacitacion {
    PROGRAMADA,
    CANCELADA
}
