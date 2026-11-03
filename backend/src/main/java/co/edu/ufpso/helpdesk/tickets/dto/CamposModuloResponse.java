package co.edu.ufpso.helpdesk.tickets.dto;

import java.util.List;

/**
 * Definición del formulario dinámico de un módulo ({@code GET /api/tickets/campos}, HU21). Los
 * campos solo se piden en incidentes; una sugerencia no lleva {@code detalle}.
 *
 * @param modulo   valor que se envía en {@code modulo}
 * @param etiqueta nombre que muestra el formulario
 * @param campos   campos del {@code detalle}, en el orden en que se muestran
 */
public record CamposModuloResponse(String modulo, String etiqueta, List<Campo> campos) {

    /**
     * Un campo del formulario.
     *
     * @param tipo           TEXTO, TEXTO_LARGO, ENTERO, DECIMAL, DINERO u OPCION
     * @param longitudMaxima solo en TEXTO y TEXTO_LARGO
     * @param opciones       solo en OPCION
     */
    public record Campo(String nombre, String etiqueta, String tipo, boolean obligatorio, Integer longitudMaxima,
            List<Opcion> opciones) {
    }

    public record Opcion(String valor, String etiqueta) {
    }
}
