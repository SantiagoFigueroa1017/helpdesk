package co.edu.ufpso.helpdesk.conocimiento.dto;

import java.util.List;

/**
 * Un resultado de búsqueda (HU30). Trae los primeros 300 caracteres del contenido para que el agente decida
 * si abre el artículo.
 *
 * @param puntaje {@code textScore} en la búsqueda por texto (sin tope, pesa más un acierto en el título) o
 *                {@code vectorSearchScore} en la semántica (de 0 a 1). Solo sirve para comparar resultados
 *                de una misma búsqueda
 */
public record ArticuloEncontradoResponse(String id, String titulo, String modulo, List<String> etiquetas,
        String visibilidad, String extracto, double puntaje) {
}
