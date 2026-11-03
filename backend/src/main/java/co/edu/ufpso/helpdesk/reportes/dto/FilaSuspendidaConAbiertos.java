package co.edu.ufpso.helpdesk.reportes.dto;

import java.time.Instant;

/**
 * Fila de A8: empresa con la membresía SUSPENDIDA y tickets abiertos. El detalle de los tickets se ve en
 * {@code GET /api/tickets?empresaId=} (decidido en 2.9).
 *
 * @param suspendidaDesde   {@code fechaActualizacion} de la membresía: cuándo la suspendió el pago fallido
 * @param abiertoMasAntiguo fecha de creación del ticket abierto más antiguo
 */
public record FilaSuspendidaConAbiertos(String empresaId, String empresa, String nit, String contacto,
        String telefono, String email, String plan, Instant suspendidaDesde, int ticketsAbiertos,
        Instant abiertoMasAntiguo) {
}
