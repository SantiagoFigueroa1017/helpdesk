package co.edu.ufpso.helpdesk.planes.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Un plan en las respuestas de la API (HU10). */
public record PlanResponse(String id, String nombre, String descripcion, BigDecimal precio, String nivelAcceso,
        Limites limites, boolean activo, Instant fechaCreacion, Instant fechaActualizacion) {

    public record Limites(int horasRespuesta, int capacitacionesIncluidas, int maxUsuarios, int maxModulos) {
    }
}
