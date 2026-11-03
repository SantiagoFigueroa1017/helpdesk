package co.edu.ufpso.helpdesk.conocimiento.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un artículo completo (HU30, detalle). Nunca trae el vector: son 768 números que no le sirven a nadie en
 * pantalla.
 *
 * @param autorNombre       "Usuario eliminado" si el autor ya no existe (modelo 1.2)
 * @param ticketsVinculados cuántos tickets resolvió con este artículo. Lo ven todos
 * @param ticketIds         los ids de esos tickets. Solo para el personal de soporte (alcance GLOBAL con
 *                          {@code TICKETS_VER}); para los demás es null, porque son tickets de otras
 *                          empresas (decidido en 2.7)
 * @param vectorPendiente   true mientras el artículo no tenga vector de Gemini. La tarea programada lo
 *                          genera; mientras tanto el artículo solo sale en la búsqueda por texto
 */
public record ArticuloResponse(String id, String titulo, String contenido, String modulo, List<String> etiquetas,
        String visibilidad, String autorId, String autorNombre, int ticketsVinculados, List<String> ticketIds,
        boolean vectorPendiente, Instant fechaCreacion, Instant fechaActualizacion) {
}
