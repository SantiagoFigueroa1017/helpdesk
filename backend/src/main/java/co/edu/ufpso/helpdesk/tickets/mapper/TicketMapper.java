package co.edu.ufpso.helpdesk.tickets.mapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.CampoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle.DefinicionModulo;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse.CambioEstadoResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResumenResponse;

/** Documentos y catálogo de tickets a DTO. La API nunca expone {@link Ticket} (contexto 8.2). */
public final class TicketMapper {

    private TicketMapper() {
    }

    // ---------- ids que hay que nombrar ----------

    /** Usuarios que aparecen en los tickets: creador, solicitante, agente y autores del historial. */
    public static Set<ObjectId> idsDeUsuarios(Collection<Ticket> tickets) {
        Set<ObjectId> ids = new HashSet<>();
        for (Ticket t : tickets) {
            ids.add(t.getCreadorId());
            ids.add(t.getSolicitanteId());
            ids.add(t.getAgenteId());
            if (t.getHistorialEstados() != null) {
                t.getHistorialEstados().forEach(c -> ids.add(c.usuarioId()));
            }
        }
        ids.remove(null);
        return ids;
    }

    public static Set<ObjectId> idsDeEmpresas(Collection<Ticket> tickets) {
        Set<ObjectId> ids = new HashSet<>();
        tickets.forEach(t -> ids.add(t.getEmpresaId()));
        return ids;
    }

    // ---------- respuestas ----------

    public static TicketResponse aRespuesta(Ticket t, NombresTicket n) {
        List<CambioEstadoResponse> historial = t.getHistorialEstados().stream()
                .map(c -> aRespuesta(c, n))
                .toList();
        return new TicketResponse(
                t.getId().toHexString(),
                t.getEmpresaId().toHexString(),
                n.empresa(t.getEmpresaId()),
                t.getCreadorId().toHexString(),
                n.usuario(t.getCreadorId()),
                t.getSolicitanteId().toHexString(),
                n.usuario(t.getSolicitanteId()),
                hex(t.getAgenteId()),
                n.usuario(t.getAgenteId()),
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

    /** Para el listado: el documento llega con proyección, sin detalle ni historial. */
    public static TicketResumenResponse aResumen(Ticket t, NombresTicket n) {
        return new TicketResumenResponse(
                t.getId().toHexString(),
                t.getEmpresaId().toHexString(),
                n.empresa(t.getEmpresaId()),
                t.getSolicitanteId().toHexString(),
                n.usuario(t.getSolicitanteId()),
                hex(t.getAgenteId()),
                n.usuario(t.getAgenteId()),
                t.getModulo().name(),
                t.getTipo().name(),
                t.getTitulo(),
                t.getEstado().name(),
                t.getFechaLimiteRespuesta(),
                t.getFechaResolucion(),
                t.getFechaCreacion());
    }

    private static CambioEstadoResponse aRespuesta(CambioEstado c, NombresTicket n) {
        return new CambioEstadoResponse(c.estado().name(), c.fecha(), c.usuarioId().toHexString(),
                n.usuario(c.usuarioId()));
    }

    // ---------- formulario dinámico ----------

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
