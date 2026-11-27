package co.edu.ufpso.helpdesk.tickets.domain;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.PersistenceCreator;

/**
 * Una entrada de {@code historialEstados}, embebida en el ticket (tope 50, HU23). La primera se
 * escribe al crear el ticket, con estado ABIERTO.
 *
 * <p>Desde 3.7 también registra las asignaciones: {@code tipo = ASIGNACION}, {@code estado} = el que
 * tenía el ticket en ese momento y {@code agenteId} = el agente nuevo (ausente si quedó sin agente).
 * En un cambio de estado {@code tipo} y {@code agenteId} no se escriben (Spring Data omite los null),
 * igual que en los documentos anteriores y en la semilla.
 *
 * @param estado    estado al que pasó el ticket (o el que tenía, en una asignación)
 * @param fecha     cuándo
 * @param usuarioId quién hizo el cambio
 * @param tipo      null = cambio de estado; ASIGNACION = cambio de agente
 * @param agenteId  solo en asignaciones: el agente nuevo, o null si se quitó
 */
public record CambioEstado(EstadoTicket estado, Instant fecha, ObjectId usuarioId, TipoEntradaHistorial tipo,
        ObjectId agenteId) {

    @PersistenceCreator
    public CambioEstado {
    }

    /** Cambio de estado. */
    public CambioEstado(EstadoTicket estado, Instant fecha, ObjectId usuarioId) {
        this(estado, fecha, usuarioId, null, null);
    }

    /** Asignación, reasignación o retiro del agente. */
    public static CambioEstado asignacion(EstadoTicket estadoActual, Instant fecha, ObjectId usuarioId,
            ObjectId agenteNuevo) {
        return new CambioEstado(estadoActual, fecha, usuarioId, TipoEntradaHistorial.ASIGNACION, agenteNuevo);
    }

    public boolean esAsignacion() {
        return tipo == TipoEntradaHistorial.ASIGNACION;
    }
}
