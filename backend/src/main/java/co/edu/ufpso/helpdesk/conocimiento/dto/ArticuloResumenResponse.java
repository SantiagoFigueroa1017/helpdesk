package co.edu.ufpso.helpdesk.conocimiento.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un artículo en el listado (HU30). Sin contenido ni vector: la consulta los deja fuera con una proyección,
 * porque un contenido llega a 50.000 caracteres y una página a 100 artículos.
 */
public record ArticuloResumenResponse(String id, String titulo, String modulo, List<String> etiquetas,
        String visibilidad, String autorNombre, int ticketsVinculados, boolean vectorPendiente,
        Instant fechaCreacion, Instant fechaActualizacion) {
}
