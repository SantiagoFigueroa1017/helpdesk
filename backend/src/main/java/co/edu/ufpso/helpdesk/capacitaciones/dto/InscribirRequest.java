package co.edu.ufpso.helpdesk.capacitaciones.dto;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo opcional de {@code POST /api/capacitaciones/{id}/inscripciones} (HU37).
 *
 * @param usuarioId vacío: el usuario de la petición se inscribe a sí mismo ({@code INSCRIPCIONES_PROPIAS}).
 *                  Con valor: el personal de soporte inscribe a ese usuario ({@code INSCRIPCIONES_GESTIONAR})
 */
public record InscribirRequest(
        @Pattern(regexp = Ids.PATRON, message = "El id del usuario no es válido.")
        String usuarioId) {
}
