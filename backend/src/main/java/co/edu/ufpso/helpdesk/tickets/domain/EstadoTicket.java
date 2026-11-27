package co.edu.ufpso.helpdesk.tickets.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del ticket. Nace ABIERTO (HU21). En los reportes, "abierto" = ABIERTO o EN_PROCESO y
 * "cerrado" = RESUELTO o CERRADO (contexto 4.2).
 *
 * <p>Flujo (DECIDIDO en 3.7; reemplaza "cualquier estado distinto del actual" de 2.6):
 * <ul>
 *   <li>ABIERTO → EN_PROCESO.</li>
 *   <li>EN_PROCESO → RESUELTO, o de vuelta a ABIERTO.</li>
 *   <li>RESUELTO → CERRADO, o EN_PROCESO si el cliente dice que no quedó resuelto.</li>
 *   <li>CERRADO es final.</li>
 * </ul>
 * Quien tiene {@code TICKETS_ASIGNAR_OTROS} (administrador) además cierra un ticket ABIERTO (duplicado
 * o no procede) y reabre uno CERRADO a EN_PROCESO.
 */
public enum EstadoTicket {
    ABIERTO,
    EN_PROCESO,
    RESUELTO,
    CERRADO;

    private static final Map<EstadoTicket, Set<EstadoTicket>> FLUJO = Map.of(
            ABIERTO, EnumSet.of(EN_PROCESO),
            EN_PROCESO, EnumSet.of(RESUELTO, ABIERTO),
            RESUELTO, EnumSet.of(CERRADO, EN_PROCESO),
            CERRADO, EnumSet.noneOf(EstadoTicket.class));

    private static final Map<EstadoTicket, Set<EstadoTicket>> SOLO_ADMINISTRADOR = Map.of(
            ABIERTO, EnumSet.of(CERRADO),
            EN_PROCESO, EnumSet.noneOf(EstadoTicket.class),
            RESUELTO, EnumSet.noneOf(EstadoTicket.class),
            CERRADO, EnumSet.of(EN_PROCESO));

    public boolean esAbierto() {
        return this == ABIERTO || this == EN_PROCESO;
    }

    /** Estados a los que se puede pasar desde este. */
    public Set<EstadoTicket> siguientes(boolean administrador) {
        Set<EstadoTicket> permitidos = EnumSet.noneOf(EstadoTicket.class);
        permitidos.addAll(FLUJO.get(this));
        if (administrador) {
            permitidos.addAll(SOLO_ADMINISTRADOR.get(this));
        }
        return permitidos;
    }

    public boolean puedePasarA(EstadoTicket nuevo, boolean administrador) {
        return siguientes(administrador).contains(nuevo);
    }
}
