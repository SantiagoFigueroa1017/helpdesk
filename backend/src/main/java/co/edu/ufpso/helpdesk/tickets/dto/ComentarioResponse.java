package co.edu.ufpso.helpdesk.tickets.dto;

import java.time.Instant;

/**
 * Un comentario de ticket en las respuestas de la API.
 *
 * @param autorNombre "Usuario eliminado" si el autor ya no existe (modelo 1.2)
 * @param editado     true si el autor lo cambió después de publicarlo
 */
public record ComentarioResponse(String id, String ticketId, String autorId, String autorNombre, String texto,
        boolean editado, Instant fechaCreacion, Instant fechaActualizacion) {
}
