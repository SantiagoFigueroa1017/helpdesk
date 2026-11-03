package co.edu.ufpso.helpdesk.tickets.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Un ticket en el detalle y en las respuestas de crear y editar (HU21 a HU23): campos comunes,
 * campos de su módulo y el historial.
 *
 * <p>Junto a cada id va el nombre, para que el frontend no tenga que buscarlo. Si el usuario ya fue
 * eliminado, el nombre es "Usuario eliminado" (modelo 1.2).
 *
 * @param agenteId        null mientras no tenga agente (y entonces {@code agenteNombre} también)
 * @param detalle         campos del módulo con valor, en el orden de {@code GET /api/tickets/campos};
 *                        null en las sugerencias
 * @param fechaResolucion null mientras no esté RESUELTO o CERRADO
 */
public record TicketResponse(String id, String empresaId, String empresaNombre, String creadorId,
        String creadorNombre, String solicitanteId, String solicitanteNombre, String agenteId, String agenteNombre,
        String modulo, String tipo, String titulo, String descripcion, Map<String, Object> detalle, String estado,
        List<CambioEstadoResponse> historialEstados, Instant fechaLimiteRespuesta, Instant fechaPrimeraAsignacion,
        Instant fechaResolucion, Instant fechaCreacion, Instant fechaActualizacion) {

    /** Una entrada del historial: a qué estado pasó, cuándo y quién lo hizo (HU23). */
    public record CambioEstadoResponse(String estado, Instant fecha, String usuarioId, String usuarioNombre) {
    }
}
