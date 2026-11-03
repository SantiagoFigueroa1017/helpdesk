package co.edu.ufpso.helpdesk.common.error;

import java.util.Map;

/**
 * 429 del login (acta 2.4). {@code ManejadorGlobalErrores} agrega la cabecera {@code Retry-After}
 * con {@link #getSegundos()}; el cuerpo lleva el mismo valor en {@code reintentarEnSegundos}.
 */
public class DemasiadosIntentosException extends ApiException {

    private final long segundos;

    public DemasiadosIntentosException(long segundos) {
        super(CodigoError.DEMASIADOS_INTENTOS, CodigoError.DEMASIADOS_INTENTOS.getDetalle(),
                Map.of("reintentarEnSegundos", segundos));
        this.segundos = segundos;
    }

    public long getSegundos() {
        return segundos;
    }
}
