package co.edu.ufpso.helpdesk.membresias.domain;

/**
 * Estados guardados de la membresía (contexto 7.5). Nace PENDIENTE (HU13). No existe un estado
 * VENCIDA: el vencimiento se calcula con {@code fechaVencimiento} en cada petición.
 */
public enum EstadoMembresia {
    PENDIENTE,
    ACTIVA,
    SUSPENDIDA
}
