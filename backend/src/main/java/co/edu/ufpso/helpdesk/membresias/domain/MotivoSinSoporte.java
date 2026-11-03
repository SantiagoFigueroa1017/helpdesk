package co.edu.ufpso.helpdesk.membresias.domain;

/**
 * Por qué una empresa no tiene soporte (contexto 7.5). Sale en el campo {@code motivo} de la
 * respuesta 403 {@code SOPORTE_BLOQUEADO} y en {@code soporte.motivo} de la membresía.
 */
public enum MotivoSinSoporte {
    /** La empresa no tiene membresía (HU01: queda sin soporte hasta que se le asigne una). */
    SIN_MEMBRESIA("La empresa no tiene una membresía asignada."),
    /** Nace así (HU13) y espera el primer pago aprobado. */
    PENDIENTE("La membresía de la empresa está pendiente de pago."),
    /** Un pago fallido la suspendió (HU17, HU19). */
    SUSPENDIDA("La membresía de la empresa está suspendida por falta de pago."),
    /** ACTIVA, pero la fecha actual superó {@code fechaVencimiento}. No es un estado guardado. */
    VENCIDA("La membresía de la empresa ya venció.");

    private final String detalle;

    MotivoSinSoporte(String detalle) {
        this.detalle = detalle;
    }

    public String getDetalle() {
        return detalle;
    }
}
