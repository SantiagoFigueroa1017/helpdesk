package co.edu.ufpso.helpdesk.membresias.domain;

/**
 * Resultado de un intento de pago. Dispara la transacción de 7.5: FALLIDO suspende la membresía;
 * APROBADO sobre una membresía PENDIENTE o SUSPENDIDA la activa.
 */
public enum ResultadoPago {
    APROBADO,
    FALLIDO
}
