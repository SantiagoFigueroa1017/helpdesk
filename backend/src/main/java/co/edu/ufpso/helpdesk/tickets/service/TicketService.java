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
import java.util.Objects;
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
import org.springframework.transaction.annotation.Transactional;

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
import co.edu.ufpso.helpdesk.conocimiento.service.ArticulosPorTicketService;
import co.edu.ufpso.helpdesk.empresas.service.NombresEmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.PermisosEfectivos;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.tickets.dto.ActualizarTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.AsignarTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.CambiarEstadoRequest;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.CrearTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.EliminacionTicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.FiltroTickets;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResumenResponse;
import co.edu.ufpso.helpdesk.tickets.mapper.DetalleTicketMapper;
import co.edu.ufpso.helpdesk.tickets.mapper.NombresTicket;
import co.edu.ufpso.helpdesk.tickets.mapper.TicketMapper;
import co.edu.ufpso.helpdesk.tickets.repository.ComentarioTicketRepository;
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
 *
 * <p>Actualizar (HU23):
 * <ul>
 *   <li>Editar con {@code TICKETS_EDITAR_PROPIOS} (cliente): título, descripción y campos del módulo
 *       de los tickets en los que es solicitante, solo si están ABIERTOS y sin agente. Con
 *       {@code TICKETS_EDITAR} (agente): además módulo y tipo, en cualquier estado. Soporte: el
 *       cliente con {@code exigirSoporte}, el agente con {@code exigirSoporteEmpresa} del ticket.</li>
 *   <li>Estado: lo cambia el agente asignado o quien tiene {@code TICKETS_ASIGNAR_OTROS}
 *       (decidido en 2.6). Cualquier estado distinto del actual (HU23). EN_PROCESO y RESUELTO exigen
 *       agente. RESUELTO o CERRADO llenan {@code fechaResolucion} si estaba vacía; ABIERTO o
 *       EN_PROCESO la vacían. Cada cambio entra al historial; con 50 entradas, 422.</li>
 *   <li>Asignación: con {@code TICKETS_ASIGNAR}, a sí mismo y solo si no tiene agente; con
 *       {@code TICKETS_ASIGNAR_OTROS}, a cualquier agente o sin agente. Agente = usuario activo de
 *       alcance GLOBAL con {@code TICKETS_ASIGNAR} (la misma definición de A3).</li>
 *   <li>Las tres escriben solo si el ticket no cambió desde que se leyó ({@code fechaActualizacion}
 *       igual): si otra persona lo cambió en medio, 409 {@code CONFLICTO} y no se pisa su cambio.</li>
 * </ul>
 *
 * <p>Eliminar (HU24): borrado físico en una transacción. Se borran los comentarios, se quita el
 * ticket de {@code articulos.ticketIds} y se borra el ticket. Si un paso falla, no se aplica ninguno.
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
    private final ComentarioTicketRepository comentarios;
    private final ArticulosPorTicketService articulos;
    private final MongoTemplate template;
    private final SoporteService soporte;
    private final MembresiaService membresias;
    private final UsuarioSesionService usuarios;
    private final NombresUsuarioService nombresUsuarios;
    private final NombresEmpresaService nombresEmpresas;
    private final PermisosEfectivos permisos;
    private final Clock reloj;

    public TicketService(TicketRepository repositorio, ComentarioTicketRepository comentarios,
            ArticulosPorTicketService articulos, MongoTemplate template, SoporteService soporte,
            MembresiaService membresias, UsuarioSesionService usuarios, NombresUsuarioService nombresUsuarios,
            NombresEmpresaService nombresEmpresas, PermisosEfectivos permisos, Clock reloj) {
        this.repositorio = repositorio;
        this.comentarios = comentarios;
        this.articulos = articulos;
        this.template = template;
        this.soporte = soporte;
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.nombresUsuarios = nombresUsuarios;
        this.nombresEmpresas = nombresEmpresas;
        this.permisos = permisos;
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
    // HU23 · Editar
    // ------------------------------------------------------------------

    public TicketResponse actualizar(ObjectId id, ActualizarTicketRequest solicitud, UsuarioAutenticado actor) {
        boolean deSoporte = actor.tiene(Permiso.TICKETS_EDITAR);
        if (!deSoporte) {
            // Sin soporte, el cliente ve sus tickets solo en lectura (HU23).
            soporte.exigirSoporte(actor);
        }
        Ticket ticket;
        if (deSoporte) {
            ticket = buscarEnAlcance(id, actor);
            soporte.exigirSoporteEmpresa(ticket.getEmpresaId());
        } else {
            ticket = buscarVisible(id, actor);
            if (!ticket.getSolicitanteId().equals(actor.id())) {
                throw new ApiException(CodigoError.ACCESO_DENEGADO,
                        "Solo puede editar los tickets en los que usted es el solicitante.");
            }
            if (ticket.getEstado() != EstadoTicket.ABIERTO || ticket.getAgenteId() != null) {
                throw new ReglaNegocioException("Solo puede editar el ticket mientras esté abierto y sin agente asignado.");
            }
        }

        ModuloErp modulo = solicitud.modulo() == null ? ticket.getModulo() : solicitud.modulo();
        TipoTicket tipo = solicitud.tipo() == null ? ticket.getTipo() : solicitud.tipo();
        boolean cambiaModulo = modulo != ticket.getModulo();
        if ((cambiaModulo || tipo != ticket.getTipo()) && !deSoporte) {
            throw new ReglaNegocioException("Solo el personal de soporte cambia el módulo o el tipo del ticket.");
        }
        DetalleTicket detalle = DetalleTicketMapper.desdeSolicitud(modulo, tipo, solicitud.detalle());
        if (cambiaModulo) {
            ContratoEmpresa contrato = membresias.contratoDeEmpresa(ticket.getEmpresaId())
                    .orElseThrow(() -> new ReglaNegocioException("La membresía de la empresa no tiene un plan vigente."));
            verificarModuloContratado(modulo, contrato);
        }

        Instant leida = ticket.getFechaActualizacion();
        ticket.setModulo(modulo);
        ticket.setTipo(tipo);
        ticket.setTitulo(solicitud.titulo().strip());
        ticket.setDescripcion(solicitud.descripcion().strip());
        ticket.setDetalle(detalle);
        ticket.setFechaActualizacion(ahora());
        return respuesta(guardar(ticket, leida));
    }

    // ------------------------------------------------------------------
    // HU23 · Cambiar estado
    // ------------------------------------------------------------------

    public TicketResponse cambiarEstado(ObjectId id, CambiarEstadoRequest solicitud, UsuarioAutenticado actor) {
        Ticket ticket = buscarEnAlcance(id, actor);
        boolean esElAsignado = actor.id().equals(ticket.getAgenteId());
        if (!esElAsignado && !actor.tiene(Permiso.TICKETS_ASIGNAR_OTROS)) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "Solo el agente asignado o un administrador cambia el estado del ticket.");
        }
        EstadoTicket nuevo = solicitud.estado();
        if (nuevo == ticket.getEstado()) {
            throw new ReglaNegocioException("El ticket ya está en estado " + nuevo + ".");
        }
        if ((nuevo == EstadoTicket.EN_PROCESO || nuevo == EstadoTicket.RESUELTO) && ticket.getAgenteId() == null) {
            throw new ReglaNegocioException("Asigne un agente antes de pasar el ticket a " + nuevo + ".");
        }
        if (ticket.getHistorialEstados().size() >= Ticket.MAX_HISTORIAL) {
            throw new ReglaNegocioException("El historial del ticket llegó al máximo de "
                    + Ticket.MAX_HISTORIAL + " cambios de estado.");
        }

        Instant leida = ticket.getFechaActualizacion();
        Instant ahora = ahora();
        ticket.setEstado(nuevo);
        ticket.getHistorialEstados().add(new CambioEstado(nuevo, ahora, actor.id()));
        if (nuevo.esAbierto()) {
            // Reabrir borra la resolución anterior (base de A1).
            ticket.setFechaResolucion(null);
        } else if (ticket.getFechaResolucion() == null) {
            // RESUELTO, o CERRADO sin pasar por RESUELTO. De RESUELTO a CERRADO se conserva la fecha.
            ticket.setFechaResolucion(ahora);
        }
        ticket.setFechaActualizacion(ahora);
        return respuesta(guardar(ticket, leida));
    }

    // ------------------------------------------------------------------
    // HU23 · Asignar
    // ------------------------------------------------------------------

    public TicketResponse asignar(ObjectId id, AsignarTicketRequest solicitud, UsuarioAutenticado actor) {
        Ticket ticket = buscarEnAlcance(id, actor);
        ObjectId nuevo = solicitud.agenteId() == null ? null : Ids.deCuerpo(solicitud.agenteId());
        if (!actor.tiene(Permiso.TICKETS_ASIGNAR_OTROS)) {
            if (!actor.id().equals(nuevo)) {
                throw new ApiException(CodigoError.ACCESO_DENEGADO, "Solo puede asignarse tickets a usted mismo.");
            }
            if (ticket.getAgenteId() != null) {
                throw new ReglaNegocioException("El ticket ya tiene agente. Solo un administrador lo reasigna.");
            }
        }
        if (!ticket.getEstado().esAbierto()) {
            throw new ReglaNegocioException("Solo se asignan tickets abiertos o en proceso.");
        }
        if (Objects.equals(nuevo, ticket.getAgenteId())) {
            throw new ReglaNegocioException(nuevo == null ? "El ticket ya está sin agente."
                    : "El ticket ya está asignado a ese agente.");
        }
        if (nuevo == null) {
            if (ticket.getEstado() != EstadoTicket.ABIERTO) {
                throw new ReglaNegocioException("Para quitar el agente, primero pase el ticket a ABIERTO.");
            }
        } else if (!esAgente(nuevo)) {
            throw new ReglaNegocioException("El usuario indicado no es un agente de soporte activo.");
        }

        Instant leida = ticket.getFechaActualizacion();
        Instant ahora = ahora();
        ticket.setAgenteId(nuevo);
        if (nuevo != null && ticket.getFechaPrimeraAsignacion() == null) {
            ticket.setFechaPrimeraAsignacion(ahora);
        }
        ticket.setFechaActualizacion(ahora);
        return respuesta(guardar(ticket, leida));
    }

    /** Usuario activo, de alcance GLOBAL, con {@code TICKETS_ASIGNAR} en un rol activo (definición de A3). */
    private boolean esAgente(ObjectId usuarioId) {
        return permisos.resolver(usuarioId)
                .filter(u -> !u.esDeEmpresa() && u.tiene(Permiso.TICKETS_ASIGNAR))
                .isPresent();
    }

    // ------------------------------------------------------------------
    // HU24 · Eliminar
    // ------------------------------------------------------------------

    /**
     * Pasos del modelo 1.2, en orden y en una sola transacción: comentarios
     * ({@code ix_comentarios_ticket_fecha}), referencias en artículos ({@code ix_articulos_ticketIds})
     * y el ticket. La confirmación previa la pide el frontend (HU24).
     */
    @Transactional
    public EliminacionTicketResponse eliminar(ObjectId id, UsuarioAutenticado actor) {
        Ticket ticket = buscarEnAlcance(id, actor);
        long comentariosEliminados = comentarios.deleteByTicketId(ticket.getId());
        long articulosDesvinculados = articulos.desvincular(ticket.getId());
        repositorio.deleteById(ticket.getId());
        return new EliminacionTicketResponse(comentariosEliminados, articulosDesvinculados);
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /**
     * Para el personal que gestiona tickets (editar con {@code TICKETS_EDITAR}, estado, asignación):
     * alcance GLOBAL ve cualquiera; alcance EMPRESA, solo los de su empresa. Fuera de alcance, 404.
     */
    Ticket buscarEnAlcance(ObjectId id, UsuarioAutenticado actor) {
        Ticket ticket = repositorio.findById(id).orElseThrow(TicketService::noExiste);
        if (actor.esDeEmpresa() && !ticket.getEmpresaId().equals(actor.empresaId())) {
            throw noExiste();
        }
        return ticket;
    }

    /**
     * Reemplaza el documento solo si sigue como se leyó. {@code findAndReplace} es atómico: busca por
     * {@code _id} y la {@code fechaActualizacion} leída, y si otra petición ya lo cambió no encuentra
     * nada. El documento no lleva {@code @Version} (el validador no lo admite, acta 2.3), así que
     * {@code fechaActualizacion} hace ese papel.
     */
    private Ticket guardar(Ticket ticket, Instant leida) {
        verificarCoherencia(ticket);
        Query mismaVersion = new Query(Criteria.where("_id").is(ticket.getId()).and("fechaActualizacion").is(leida));
        if (template.findAndReplace(mismaVersion, ticket) == null) {
            throw new ApiException(CodigoError.CONFLICTO,
                    "Otra persona cambió el ticket mientras usted lo editaba. Recárguelo e intente de nuevo.");
        }
        return ticket;
    }

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
