package co.edu.ufpso.helpdesk.tickets.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.domain.ComentarioTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.dto.ComentarioRequest;
import co.edu.ufpso.helpdesk.tickets.dto.ComentarioResponse;
import co.edu.ufpso.helpdesk.tickets.mapper.NombresTicket;
import co.edu.ufpso.helpdesk.tickets.repository.ComentarioTicketRepository;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;

/**
 * Comentarios de ticket (CRUD fuera de las 40, modelo 1.2 sección 8, tarea 2.6).
 *
 * <ul>
 *   <li>Se ven y se escriben solo en tickets que el usuario puede ver (misma regla de HU22). Un
 *       ticket o comentario fuera de su vista responde 404.</li>
 *   <li>Crear y editar: solo con el ticket abierto (ABIERTO o EN_PROCESO) y con soporte. El cliente
 *       pasa por {@code exigirSoporte}; el agente, por {@code exigirSoporteEmpresa} del ticket
 *       (contexto 7.5, decidido en 2.5).</li>
 *   <li>Editar: solo el autor, y el comentario queda marcado como editado.</li>
 *   <li>Eliminar: el autor, o quien tiene {@code COMENTARIOS_ELIMINAR} (de cualquier autor). No
 *       depende del soporte ni del estado del ticket.</li>
 * </ul>
 */
@Service
public class ComentarioService {

    /** Solo por fecha: es el segundo campo de {@code ix_comentarios_ticket_fecha}. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaCreacion");

    private final ComentarioTicketRepository repositorio;
    private final TicketService tickets;
    private final SoporteService soporte;
    private final NombresUsuarioService nombres;
    private final Clock reloj;

    public ComentarioService(ComentarioTicketRepository repositorio, TicketService tickets, SoporteService soporte,
            NombresUsuarioService nombres, Clock reloj) {
        this.repositorio = repositorio;
        this.tickets = tickets;
        this.soporte = soporte;
        this.nombres = nombres;
        this.reloj = reloj;
    }

    /** La conversación, de la más antigua a la más reciente salvo que se pida otro sentido. */
    public Pagina<ComentarioResponse> listar(ObjectId ticketId, Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.ASC, "fechaCreacion"));
        Ticket ticket = tickets.buscarVisible(ticketId, actor);
        Page<ComentarioTicket> pagina = repositorio.findByTicketId(ticket.getId(), conOrden);
        Map<ObjectId, String> autores = nombres.nombres(pagina.map(ComentarioTicket::getAutorId).getContent());
        return Pagina.de(pagina, c -> aRespuesta(c, autores));
    }

    public ComentarioResponse crear(ObjectId ticketId, ComentarioRequest solicitud, UsuarioAutenticado actor) {
        soporte.exigirSoporte(actor);
        Ticket ticket = tickets.buscarVisible(ticketId, actor);
        verificarAbiertoYConSoporte(ticket, actor);

        Instant ahora = ahora();
        ComentarioTicket comentario = new ComentarioTicket();
        comentario.setFechaCreacion(ahora);
        comentario.setFechaActualizacion(ahora);
        comentario.setTicketId(ticket.getId());
        comentario.setAutorId(actor.id());
        comentario.setTexto(solicitud.texto().strip());
        comentario.setEditado(false);
        return aRespuesta(repositorio.insert(comentario), Map.of(actor.id(), actor.nombre()));
    }

    public ComentarioResponse actualizar(ObjectId id, ComentarioRequest solicitud, UsuarioAutenticado actor) {
        soporte.exigirSoporte(actor);
        ComentarioTicket comentario = buscar(id);
        Ticket ticket = tickets.buscarVisible(comentario.getTicketId(), actor);
        if (!comentario.getAutorId().equals(actor.id())) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO, "Solo el autor puede editar su comentario.");
        }
        verificarAbiertoYConSoporte(ticket, actor);

        comentario.setTexto(solicitud.texto().strip());
        comentario.setEditado(true);
        comentario.setFechaActualizacion(ahora());
        return aRespuesta(repositorio.save(comentario), Map.of(actor.id(), actor.nombre()));
    }

    public void eliminar(ObjectId id, UsuarioAutenticado actor) {
        ComentarioTicket comentario = buscar(id);
        if (actor.tiene(Permiso.COMENTARIOS_ELIMINAR)) {
            tickets.buscarEnAlcance(comentario.getTicketId(), actor);
        } else {
            tickets.buscarVisible(comentario.getTicketId(), actor);
            if (!comentario.getAutorId().equals(actor.id())) {
                throw new ApiException(CodigoError.ACCESO_DENEGADO, "Solo el autor puede eliminar su comentario.");
            }
        }
        repositorio.deleteById(id);
    }

    // ---------- reglas ----------

    /**
     * Ticket abierto y con soporte. Para el cliente, {@code exigirSoporte} ya se revisó al entrar;
     * para el agente, que actúa sobre la empresa del ticket, se revisa aquí.
     */
    private void verificarAbiertoYConSoporte(Ticket ticket, UsuarioAutenticado actor) {
        if (!actor.esDeEmpresa()) {
            soporte.exigirSoporteEmpresa(ticket.getEmpresaId());
        }
        if (!ticket.getEstado().esAbierto()) {
            throw new ReglaNegocioException("Solo se comenta en tickets abiertos o en proceso. Este está "
                    + ticket.getEstado() + ".");
        }
    }

    private ComentarioTicket buscar(ObjectId id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comentario solicitado."));
    }

    private static ComentarioResponse aRespuesta(ComentarioTicket c, Map<ObjectId, String> autores) {
        return new ComentarioResponse(
                c.getId().toHexString(),
                c.getTicketId().toHexString(),
                c.getAutorId().toHexString(),
                autores.getOrDefault(c.getAutorId(), NombresTicket.USUARIO_ELIMINADO),
                c.getTexto(),
                Boolean.TRUE.equals(c.getEditado()),
                c.getFechaCreacion(),
                c.getFechaActualizacion());
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
