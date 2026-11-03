package co.edu.ufpso.helpdesk.tickets.dto;

import java.util.Map;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/tickets/{id}} (HU23). Reemplaza título, descripción y {@code detalle}.
 * La empresa, el solicitante, el estado y el agente no cambian aquí: el estado y el agente tienen
 * sus propios endpoints.
 *
 * @param modulo  opcional. Vacío o igual al actual: no cambia. Solo el personal de soporte
 *                ({@code TICKETS_EDITAR}) lo cambia, y el nuevo debe estar contratado
 * @param tipo    opcional, con la misma regla que {@code modulo}
 * @param detalle campos del módulo, completos: lo que no venga se borra. Debe cumplir la estructura
 *                del módulo (HU23)
 */
public record ActualizarTicketRequest(
        ModuloErp modulo,

        TipoTicket tipo,

        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 150, message = "El título admite como máximo 150 caracteres.")
        String titulo,

        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 5000, message = "La descripción admite como máximo 5000 caracteres.")
        String descripcion,

        Map<String, Object> detalle) {
}
