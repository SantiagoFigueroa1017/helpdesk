package co.edu.ufpso.helpdesk.membresias.service;

/**
 * Si una empresa tiene soporte en este momento (contexto 7.5).
 *
 * @param habilitado true si la membresía existe, está ACTIVA y no ha vencido
 * @param motivo     null si hay soporte; si no, {@code SIN_MEMBRESIA}, {@code PENDIENTE},
 *                   {@code SUSPENDIDA} o {@code VENCIDA}
 */
public record EstadoSoporte(boolean habilitado, String motivo) {
}
