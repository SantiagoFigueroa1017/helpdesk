package co.edu.ufpso.helpdesk.common.error;

/**
 * 404. También se lanza cuando el recurso existe pero pertenece a otra empresa (contexto 8.3):
 * la respuesta es la misma para no confirmar que el id existe.
 */
public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException() {
        super(CodigoError.RECURSO_NO_ENCONTRADO);
    }

    /** @param detalle por ejemplo "No existe el ticket solicitado." */
    public RecursoNoEncontradoException(String detalle) {
        super(CodigoError.RECURSO_NO_ENCONTRADO, detalle);
    }
}
