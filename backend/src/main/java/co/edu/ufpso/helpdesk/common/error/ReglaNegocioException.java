package co.edu.ufpso.helpdesk.common.error;

/**
 * 422. Los datos llegan bien formados pero la operación incumple una regla que solo el backend
 * puede revisar: un límite del plan, una fecha de fin anterior a la de inicio, un cupo lleno.
 */
public class ReglaNegocioException extends ApiException {

    public ReglaNegocioException(String detalle) {
        super(CodigoError.REGLA_NEGOCIO, detalle);
    }
}
