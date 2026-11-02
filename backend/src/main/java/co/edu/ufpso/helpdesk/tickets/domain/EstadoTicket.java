package co.edu.ufpso.helpdesk.tickets.domain;

/**
 * Estados del ticket. Nace ABIERTO (HU21). En los reportes, "abierto" = ABIERTO o EN_PROCESO y
 * "cerrado" = RESUELTO o CERRADO (contexto 4.2).
 */
public enum EstadoTicket {
    ABIERTO,
    EN_PROCESO,
    RESUELTO,
    CERRADO;

    public boolean esAbierto() {
        return this == ABIERTO || this == EN_PROCESO;
    }
}
