package co.edu.ufpso.helpdesk.reportes.dto;

/**
 * Fila de A4: plan contratado y totales de tickets de una empresa.
 *
 * @param plan            nombre del plan, o "Sin membresía"
 * @param estadoMembresia PENDIENTE, ACTIVA, SUSPENDIDA, VENCIDA (derivada: ACTIVA con la fecha pasada) o "-"
 * @param modulos         cantidad de módulos contratados
 */
public record FilaPlanTicketsEmpresa(String empresaId, String nit, String razonSocial, String plan,
        String estadoMembresia, int modulos, int tickets, int abiertos, int incidentes, int sugerencias) {
}
