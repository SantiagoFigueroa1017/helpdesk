package co.edu.ufpso.helpdesk.tickets.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Un ticket en las respuestas de la API (HU21 a HU23): campos comunes y campos de su módulo.
 *
 * @param agenteId       null mientras no tenga agente
 * @param detalle        campos del módulo con valor, en el orden de {@code GET /api/tickets/campos};
 *                       null en las sugerencias
 * @param fechaResolucion null mientras no esté RESUELTO o CERRADO
 */
public record TicketResponse(String id, String empresaId, String creadorId, String solicitanteId, String agenteId,
        String modulo, String tipo, String titulo, String descripcion, Map<String, Object> detalle, String estado,
        List<CambioEstadoResponse> historialEstados, Instant fechaLimiteRespuesta, Instant fechaPrimeraAsignacion,
        Instant fechaResolucion, Instant fechaCreacion, Instant fechaActualizacion) {

    /** Una entrada del historial: a qué estado pasó, cuándo y quién lo hizo (HU23). */
    public record CambioEstadoResponse(String estado, Instant fecha, String usuarioId) {
    }
}
