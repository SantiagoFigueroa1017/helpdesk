package co.edu.ufpso.helpdesk.common.error;

import java.util.Map;

/**
 * 409. La operación choca con datos que ya existen: un registro repetido o un borrado que
 * las reglas del modelo bloquean porque hay dependientes.
 */
public class ConflictoException extends ApiException {

    public ConflictoException(String detalle) {
        super(CodigoError.CONFLICTO, detalle);
    }

    private ConflictoException(CodigoError codigo, String detalle, Map<String, ?> propiedades) {
        super(codigo, detalle, propiedades);
    }

    /** Para comprobar antes de insertar, por ejemplo un NIT o un email que ya están registrados. */
    public static ConflictoException duplicado(String detalle) {
        return new ConflictoException(CodigoError.DUPLICADO, detalle, Map.of());
    }

    /**
     * Para los borrados bloqueados (empresa, usuario, plan, membresía, rol).
     *
     * @param dependientes cuántos registros bloquean el borrado, por colección. Ejemplo:
     *                     {@code Map.of("usuarios", 12L, "tickets", 40L)}. Sale en la respuesta
     *                     como {@code dependientes} para que el frontend explique por qué.
     */
    public static ConflictoException tieneDependientes(String detalle, Map<String, Long> dependientes) {
        return new ConflictoException(CodigoError.TIENE_DEPENDIENTES, detalle,
                Map.of("dependientes", Map.copyOf(dependientes)));
    }
}
