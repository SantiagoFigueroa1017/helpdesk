package co.edu.ufpso.helpdesk.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * Error esperado que lanza un servicio. {@link ManejadorGlobalErrores} lo convierte en un
 * ProblemDetail con el estado de su {@link CodigoError}.
 *
 * <p>Las propiedades extra salen tal cual en la respuesta, así que nunca deben llevar
 * contraseñas, tokens ni datos de otra empresa. No admiten valores nulos.
 *
 * <p>Ejemplo para la verificación de soporte (7.5):
 * {@code throw new ApiException(CodigoError.SOPORTE_BLOQUEADO);}
 */
public class ApiException extends RuntimeException {

    private final CodigoError codigo;
    private final transient Map<String, Object> propiedades;

    public ApiException(CodigoError codigo) {
        this(codigo, codigo.getDetalle());
    }

    public ApiException(CodigoError codigo, String detalle) {
        this(codigo, detalle, Map.of());
    }

    public ApiException(CodigoError codigo, String detalle, Map<String, ?> propiedades) {
        super(detalle);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.propiedades = Map.copyOf(propiedades);
    }

    public CodigoError getCodigo() {
        return codigo;
    }

    public Map<String, Object> getPropiedades() {
        return propiedades;
    }
}
