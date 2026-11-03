package co.edu.ufpso.helpdesk.tickets.mapper;

import java.util.List;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.CampoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle.DefinicionModulo;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse.CambioEstadoResponse;

/** Documentos y catálogo de tickets a DTO. La API nunca expone {@link Ticket} (contexto 8.2). */
public final class TicketMapper {

    private TicketMapper() {
    }

    public static TicketResponse aRespuesta(Ticket t) {
        List<CambioEstadoResponse> historial = t.getHistorialEstados().stream()
                .map(TicketMapper::aRespuesta)
                .toList();
        return new TicketResponse(
                t.getId().toHexString(),
                t.getEmpresaId().toHexString(),
                t.getCreadorId().toHexString(),
                t.getSolicitanteId().toHexString(),
                hex(t.getAgenteId()),
                t.getModulo().name(),
                t.getTipo().name(),
                t.getTitulo(),
                t.getDescripcion(),
                DetalleTicketMapper.aRespuesta(t.getDetalle()),
                t.getEstado().name(),
                historial,
                t.getFechaLimiteRespuesta(),
                t.getFechaPrimeraAsignacion(),
                t.getFechaResolucion(),
                t.getFechaCreacion(),
                t.getFechaActualizacion());
    }

    private static CambioEstadoResponse aRespuesta(CambioEstado c) {
        return new CambioEstadoResponse(c.estado().name(), c.fecha(), c.usuarioId().toHexString());
    }

    /** Los cinco módulos del catálogo, para {@code GET /api/tickets/campos}. */
    public static List<CamposModuloResponse> campos() {
        return CatalogoDetalle.todos().stream().map(TicketMapper::aRespuesta).toList();
    }

    private static CamposModuloResponse aRespuesta(DefinicionModulo d) {
        return new CamposModuloResponse(d.modulo().name(), d.etiqueta(),
                d.campos().stream().map(TicketMapper::aRespuesta).toList());
    }

    private static CamposModuloResponse.Campo aRespuesta(CampoDetalle c) {
        List<CamposModuloResponse.Opcion> opciones = c.opciones().stream()
                .map(o -> new CamposModuloResponse.Opcion(o.valor(), o.etiqueta()))
                .toList();
        return new CamposModuloResponse.Campo(c.nombre(), c.etiqueta(), c.tipo().name(), c.obligatorio(),
                c.longitudMaxima(), opciones);
    }

    private static String hex(ObjectId id) {
        return id == null ? null : id.toHexString();
    }
}
