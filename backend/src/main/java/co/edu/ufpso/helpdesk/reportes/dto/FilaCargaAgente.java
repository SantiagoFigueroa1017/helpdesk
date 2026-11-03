package co.edu.ufpso.helpdesk.reportes.dto;

import java.time.Instant;

/**
 * Fila de A3: tickets abiertos (ABIERTO o EN_PROCESO) asignados a un agente.
 *
 * @param masAntiguo fecha de creación del ticket abierto más antiguo; null si no tiene abiertos
 */
public record FilaCargaAgente(String usuarioId, String nombre, String email, boolean activo, int abiertos,
        int enProceso, Instant masAntiguo) {
}
