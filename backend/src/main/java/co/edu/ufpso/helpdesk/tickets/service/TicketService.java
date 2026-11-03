package co.edu.ufpso.helpdesk.tickets.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ErrorCampo;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.empresas.service.NombresEmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.CrearTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.FiltroTickets;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResumenResponse;
import co.edu.ufpso.helpdesk.tickets.mapper.DetalleTicketMapper;
import co.edu.ufpso.helpdesk.tickets.mapper.NombresTicket;
import co.edu.ufpso.helpdesk.tickets.mapper.TicketMapper;
import co.edu.ufpso.helpdesk.tickets.repository.TicketRepository;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesion;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;

/**
 * Tickets polimórficos (HU21 a HU24, modelo 1.2 sección 7, tarea 2.6).
 *
 * <p>Crear (HU21):
 * <ul>
 *   <li>Usuario de empresa: crea en su empresa y siempre es el solicitante (HU21). Si envía otro
 *       solicitante, 422.</li>
 *   <li>Personal de soporte (GLOBAL): crea en nombre de un cliente y debe indicar el solicitante, que
 *       existe, está activo y pertenece a una empresa. La empresa del ticket es la del solicitante.</li>
 *   <li>Soporte (contexto 7.5): sin membresía ACTIVA y vigente responde 403 {@code SOPORTE_BLOQUEADO}
 *       con {@code motivo}, también cuando crea un agente (decidido en 2.5).</li>
 *   <li>El módulo debe estar contratado en la membresía: 422 (HU21 v.4).</li>
 *   <li>Nace ABIERTO, sin agente, con la primera entrada del historial y la fecha límite de
 *       respuesta = creación + {@code horasRespuesta} del plan. No cambia si el plan cambia (HU11).</li>
 * </ul>
 *
 * <p>Consultar (HU22). Qué tickets ve cada uno:
 * <ul>
 *   <li>Con {@code TICKETS_VER} y alcance GLOBAL (agente, administrador): todos.</li>
 *   <li>Con {@code TICKETS_VER} y alcance EMPRESA (CLIENTE_ADMIN): los de su empresa.</li>
 *   <li>Solo con {@code TICKETS_VER_PROPIOS} (CLIENTE): aquellos en los que es solicitante, también
 *       los que un agente creó a su nombre.</li>
 *   <li>Un ticket que no puede ver responde 404, igual que uno que no existe (IDOR, contexto 6.5).</li>
 * </ul>
 * La consulta no depende del soporte: sin membresía vigente, el cliente sigue viendo sus tickets
 * (HU23, modo lectura).
 */
@Service
public class TicketService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /**
     * Orden permitido en el listado. {@code fechaCreacion} es el último campo de los índices que
     * empiezan por empresa, solicitante, agente o módulo; {@code fechaLimiteRespuesta} va con
     * {@code ix_tickets_estado_limite} cuando se filtra por estado (bandeja del agente, HU22 v.4).
     */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaCreacion", "fechaLimiteRespuesta");

    /** Campos que trae el listado; el resto (descripción, detalle, historial) queda en la base. */
    private static final String[] CAMPOS_RESUMEN = { "empresaId", "solicitanteId", "agenteId", "modulo", "tipo",
            "titulo", "estado", "fechaLimiteRespuesta", "fechaResolucion", "fechaCreacion" };

    private final TicketRepository repositorio;
    private final MongoTemplate template;
    private final SoporteService soporte;
    private final MembresiaService membresias;
    private final UsuarioSesionService usuarios;
    private final NombresUsuarioService nombresUsuarios;
    private final NombresEmpresaService nombresEmpresas;
    private final Clock reloj;

    public TicketService(TicketRepository repositorio, MongoTemplate template, SoporteService soporte,
            MembresiaService membresias, UsuarioSesionService usuarios, NombresUsuarioService nombresUsuarios,
            NombresEmpresaService nombresEmpresas, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.soporte = soporte;
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.nombresUsuarios = nombresUsuarios;
        this.nombresEmpresas = nombresEmpresas;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Formulario dinámico
    // ------------------------------------------------------------------

    /** Campos del {@code detalle} de cada módulo. Es fijo: sale del catálogo, no de la base. */
    public List<CamposModuloResponse> campos() {
        return TicketMapper.campos();
    }

    // ------------------------------------------------------------------
    // HU22 · Consultar
    // ------------------------------------------------------------------

    /**
     * Listado paginado. Ejemplos: {@code ?estado=ABIERTO&orden=fechaLimiteRespuesta,asc} (bandeja del
     * agente), {@code ?modulo=COMPRAS&desde=2026-10-01&hasta=2026-10-31}.
     */
    public Pagina<TicketResumenResponse> listar(FiltroTickets filtro, Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        Query consulta = new Query();
        Criteria alcance = alcance(actor);
        if (alcance != null) {
            consulta.addCriteria(alcance);
        }
        agregarFiltros(consulta, filtro, actor);

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(consulta, Ticket.class);
        consulta.fields().include(CAMPOS_RESUMEN);
        List<Ticket> tickets = template.find(consulta.with(conOrden), Ticket.class);
        NombresTicket nombres = nombres(tickets);
        return Pagina.de(new PageImpl<>(tickets, conOrden, total), t -> TicketMapper.aResumen(t, nombres));
    }

    public TicketResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        return respuesta(buscarVisible(id, actor));
    }

    /**
     * Qué parte de la colección puede ver el actor. Nunca usa un id que llegue en la petición: la
     * empresa y el usuario salen del token (contexto 8.3). null = sin restricción (personal de soporte).
     */
    private static Criteria alcance(UsuarioAutenticado actor) {
        if (actor.tiene(Permiso.TICKETS_VER)) {
            return actor.esDeEmpresa() ? Criteria.where("empresaId").is(actor.empresaId()) : null;
        }
        return Criteria.where("solicitanteId").is(actor.id());
    }

    /** Misma regla que {@link #alcance}, aplicada a un ticket ya leído. */
    private static boolean puedeVer(Ticket ticket, UsuarioAutenticado actor) {
        if (actor.tiene(Permiso.TICKETS_VER)) {
            return !actor.esDeEmpresa() || ticket.getEmpresaId().equals(actor.empresaId());
        }
        return ticket.getSolicitanteId().equals(actor.id());
    }

    private void agregarFiltros(Query consulta, FiltroTickets f, UsuarioAutenticado actor) {
        if (f.estado() != null) {
            consulta.addCriteria(Criteria.where("estado").is(f.estado()));
        }
        if (f.modulo() != null) {
            consulta.addCriteria(Criteria.where("modulo").is(f.modulo()));
        }
        if (f.tipo() != null) {
            consulta.addCriteria(Criteria.where("tipo").is(f.tipo()));
        }
        // A un usuario de empresa ya se le filtró la suya en alcance(): el parámetro no aplica.
        ObjectId empresa = Ids.deParametro("empresaId", f.empresaId());
        if (empresa != null && !actor.esDeEmpresa()) {
            consulta.addCriteria(Criteria.where("empresaId").is(empresa));
        }
        ObjectId agente = Ids.deParametro("agenteId", f.agenteId());
        boolean sinAsignar = Boolean.TRUE.equals(f.sinAsignar());
        if (agente != null && sinAsignar) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "Use 'agenteId' o 'sinAsignar=true', no los dos a la vez.");
        }
        if (agente != null) {
            consulta.addCriteria(Criteria.where("agenteId").is(agente));
        } else if (sinAsignar) {
            consulta.addCriteria(Criteria.where("agenteId").is(null));
        }
        if (f.desde() != null || f.hasta() != null) {
            if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
                throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                        "La fecha 'desde' no puede ser posterior a 'hasta'.");
            }
            Criteria fecha = Criteria.where("fechaCreacion");
            if (f.desde() != null) {
                fecha = fecha.gte(f.desde().atStartOfDay(BOGOTA).toInstant());
            }
            if (f.hasta() != null) {
                // Hasta el final del día: menor que el comienzo del día siguiente.
                fecha = fecha.lt(f.hasta().plusDays(1).atStartOfDay(BOGOTA).toInstant());
            }
            consulta.addCriteria(fecha);
        }
    }

    // ------------------------------------------------------------------
    // HU21 · Crear
    // ------------------------------------------------------------------

    public TicketResponse crear(CrearTicketRequest solicitud, UsuarioAutenticado actor) {
        ObjectId empresaId;
        ObjectId solicitanteId;
        if (actor.esDeEmpresa()) {
            soporte.exigirSoporte(actor);
            ObjectId pedido = solicitud.solicitanteId() == null ? null : Ids.deCuerpo(solicitud.solicitanteId());
            if (pedido != null && !pedido.equals(actor.id())) {
                throw new ReglaNegocioException("Solo el personal de soporte crea tickets en nombre de otro usuario. "
                        + "Deje el solicitante vacío.");
            }
            empresaId = actor.empresaId();
            solicitanteId = actor.id();
        } else {
            UsuarioSesion solicitante = solicitanteDeAgente(solicitud.solicitanteId());
            // El agente necesita la empresa del solicitante para saber qué membresía revisar.
            soporte.exigirSoporteEmpresa(solicitante.empresaId());
            empresaId = solicitante.empresaId();
            solicitanteId = solicitante.id();
        }

        DetalleTicket detalle = DetalleTicketMapper.desdeSolicitud(solicitud.modulo(), solicitud.tipo(),
                solicitud.detalle());
        ContratoEmpresa contrato = membresias.contratoDeEmpresa(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("La membresía de la empresa no tiene un plan vigente."));
        verificarModuloContratado(solicitud.modulo(), contrato);

        Instant ahora = ahora();
        Ticket ticket = new Ticket();
        ticket.setFechaCreacion(ahora);
        ticket.setFechaActualizacion(ahora);
        ticket.setEmpresaId(empresaId);
        ticket.setCreadorId(actor.id());
        ticket.setSolicitanteId(solicitanteId);
        ticket.setAgenteId(null);
        ticket.setModulo(solicitud.modulo());
        ticket.setTipo(solicitud.tipo());
        ticket.setTitulo(solicitud.titulo().strip());
        ticket.setDescripcion(solicitud.descripcion().strip());
        ticket.setDetalle(detalle);
        ticket.setEstado(EstadoTicket.ABIERTO);
        ticket.setHistorialEstados(new ArrayList<>(List.of(new CambioEstado(EstadoTicket.ABIERTO, ahora, actor.id()))));
        ticket.setFechaResolucion(null);
        ticket.setFechaPrimeraAsignacion(null);
        ticket.setFechaLimiteRespuesta(ahora.plus(Duration.ofHours(contrato.plan().limites().horasRespuesta())));
        verificarCoherencia(ticket);
        return respuesta(repositorio.insert(ticket));
    }

    /** El solicitante que indica un agente: obligatorio, existe, activo y de una empresa cliente. */
    private UsuarioSesion solicitanteDeAgente(String solicitanteId) {
        if (solicitanteId == null) {
            throw new ApiException(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                    Map.of("errores", List.of(new ErrorCampo("solicitanteId",
                            "Indique el cliente al que pertenece el ticket."))));
        }
        UsuarioSesion solicitante = usuarios.buscarParaSesion(Ids.deCuerpo(solicitanteId))
                .orElseThrow(() -> new ReglaNegocioException("El solicitante indicado no existe."));
        if (solicitante.empresaId() == null) {
            throw new ReglaNegocioException("El solicitante debe ser un usuario de una empresa cliente.");
        }
        if (!solicitante.activo()) {
            throw new ReglaNegocioException("El solicitante está inactivo.");
        }
        return solicitante;
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /**
     * El ticket si el actor puede verlo; si no, 404. Lo usan el detalle y, en las partes siguientes,
     * editar, comentar y eliminar.
     */
    Ticket buscarVisible(ObjectId id, UsuarioAutenticado actor) {
        Ticket ticket = repositorio.findById(id).orElseThrow(TicketService::noExiste);
        if (!puedeVer(ticket, actor)) {
            throw noExiste();
        }
        return ticket;
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe el ticket solicitado.");
    }

    /** Respuesta completa de un ticket, con los nombres resueltos. */
    private TicketResponse respuesta(Ticket ticket) {
        return TicketMapper.aRespuesta(ticket, nombres(List.of(ticket)));
    }

    /** Una consulta a usuarios y una a empresas para toda la página. */
    private NombresTicket nombres(List<Ticket> tickets) {
        return new NombresTicket(nombresUsuarios.nombres(TicketMapper.idsDeUsuarios(tickets)),
                nombresEmpresas.nombres(TicketMapper.idsDeEmpresas(tickets)));
    }

    private static void verificarModuloContratado(ModuloErp modulo, ContratoEmpresa contrato) {
        if (!contrato.modulos().contains(modulo)) {
            String contratados = EnumSet.copyOf(contrato.modulos()).stream()
                    .map(m -> CatalogoDetalle.de(m).etiqueta())
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException("La empresa no tiene contratado el módulo "
                    + CatalogoDetalle.de(modulo).etiqueta() + ". Módulos contratados: " + contratados + ".");
        }
    }

    /**
     * Misma regla del {@code oneOf}: responde 422 antes de que MongoDB rechace el documento con un
     * 500. Con el mapper no debería fallar nunca; es la red de seguridad de 2.3.
     */
    private static void verificarCoherencia(Ticket ticket) {
        if (!ticket.detalleCoherente()) {
            throw new ReglaNegocioException("Los campos específicos no corresponden al módulo y tipo del ticket.");
        }
        if (ticket.getHistorialEstados().size() > Ticket.MAX_HISTORIAL) {
            throw new ReglaNegocioException("El historial del ticket llegó al máximo de "
                    + Ticket.MAX_HISTORIAL + " cambios de estado.");
        }
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
