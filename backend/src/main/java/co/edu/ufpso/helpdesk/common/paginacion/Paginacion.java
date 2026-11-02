package co.edu.ufpso.helpdesk.common.paginacion;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;

/**
 * Reglas de paginación de toda la API.
 *
 * <p>Parámetros de la URL: {@code pagina} (desde 0), {@code tamano} y {@code orden}, por
 * ejemplo {@code ?pagina=0&tamano=20&orden=fechaCreacion,desc}. Si no llegan, se usa la
 * página 0 con {@value #TAMANO_POR_DEFECTO} elementos. Un {@code tamano} mayor que
 * {@value #TAMANO_MAXIMO} se recorta a {@value #TAMANO_MAXIMO} sin error.
 */
public final class Paginacion {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    public static final String PARAMETRO_PAGINA = "pagina";
    public static final String PARAMETRO_TAMANO = "tamano";
    public static final String PARAMETRO_ORDEN = "orden";

    private Paginacion() {
    }

    /**
     * Rechaza un orden por un campo que el endpoint no permite. Cada endpoint declara solo los
     * campos que tienen índice, para que un {@code orden} arbitrario no obligue a MongoDB a
     * recorrer la colección completa ni a ordenar en memoria.
     *
     * <pre>{@code
     * Paginacion.validarOrden(pageable, Set.of("fechaCreacion", "prioridad"));
     * }</pre>
     *
     * @throws ApiException con {@link CodigoError#PARAMETRO_INVALIDO} (400)
     */
    public static void validarOrden(Pageable pageable, Set<String> camposPermitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!camposPermitidos.contains(orden.getProperty())) {
                throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                        "No se puede ordenar por '" + orden.getProperty() + "'. Campos permitidos: "
                                + String.join(", ", new TreeSet<>(camposPermitidos)) + ".");
            }
        }
    }
}
