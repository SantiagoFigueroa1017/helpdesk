package co.edu.ufpso.helpdesk.tickets.domain;

import java.util.List;

/**
 * Un campo del {@code detalle} de un incidente, tal como lo define {@link CatalogoDetalle}.
 *
 * @param nombre         nombre del campo en el documento y en el JSON (igual al componente del record)
 * @param etiqueta       texto que muestra el formulario
 * @param tipo           cómo se dibuja y se convierte
 * @param obligatorio    si el validador lo exige
 * @param longitudMaxima tope de caracteres para TEXTO y TEXTO_LARGO; null en los demás tipos
 * @param opciones       valores permitidos para OPCION; vacío en los demás tipos
 */
public record CampoDetalle(String nombre, String etiqueta, TipoCampo tipo, boolean obligatorio,
        Integer longitudMaxima, List<Opcion> opciones) {

    public CampoDetalle {
        opciones = List.copyOf(opciones);
    }

    /**
     * Un valor permitido de un campo OPCION.
     *
     * @param valor    lo que se envía y se guarda (nombre de la constante del enum)
     * @param etiqueta lo que se muestra
     */
    public record Opcion(String valor, String etiqueta) {
    }
}
