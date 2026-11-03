package co.edu.ufpso.helpdesk.tickets.dto;

import java.util.Map;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/tickets} (HU21).
 *
 * <p>No trae {@code empresaId}: un usuario de empresa crea en la suya y un agente crea para la
 * empresa del solicitante. Así una empresa y un solicitante que no coinciden no pueden llegar.
 *
 * @param solicitanteId solo para el personal de soporte (alcance GLOBAL), que crea en nombre de un
 *                      cliente y debe indicarlo. Un usuario de empresa siempre es su propio
 *                      solicitante (HU21) y lo deja vacío
 * @param detalle       campos del módulo, solo en incidentes. Su forma depende de {@code modulo} y la
 *                      revisa {@code DetalleTicketMapper} contra {@code GET /api/tickets/campos}
 */
public record CrearTicketRequest(
        @Pattern(regexp = Ids.PATRON, message = "El id del solicitante no es válido.")
        String solicitanteId,

        @NotNull(message = "El módulo es obligatorio.")
        ModuloErp modulo,

        @NotNull(message = "El tipo es obligatorio.")
        TipoTicket tipo,

        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 150, message = "El título admite como máximo 150 caracteres.")
        String titulo,

        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 5000, message = "La descripción admite como máximo 5000 caracteres.")
        String descripcion,

        Map<String, Object> detalle) {
}
