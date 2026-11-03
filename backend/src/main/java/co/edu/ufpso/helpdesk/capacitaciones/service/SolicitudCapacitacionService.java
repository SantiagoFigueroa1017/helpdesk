package co.edu.ufpso.helpdesk.capacitaciones.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoSolicitud;
import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CrearSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.ProgramarSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.RechazarSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.SolicitudResponse;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.CapacitacionMapper;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.NombresCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.repository.SolicitudCapacitacionRepository;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.common.transaccion.TransaccionConReintento;
import co.edu.ufpso.helpdesk.empresas.service.NombresEmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;

/**
 * Solicitudes de capacitación personalizada (CRUD fuera de las 40; HU33 v.4; modelo 1.2 sección 11;
 * tarea 2.8).
 *
 * <p>Crear (CLIENTE_ADMIN, {@code CAPACITACIONES_SOLICITAR}):
 * <ul>
 *   <li>La empresa es siempre la del usuario. Sin soporte, 403 {@code SOPORTE_BLOQUEADO} (HU33 v.4).</li>
 *   <li>El módulo debe estar contratado (422).</li>
 *   <li>La fecha preferida, si llega, no es anterior a hoy en hora de Bogotá (422).</li>
 *   <li>Límite del plan: en el mes pagado vigente la empresa no tiene más solicitudes PENDIENTE o
 *       PROGRAMADA que {@code capacitacionesIncluidas} (decidido en 2.8). Las rechazadas no cuentan.</li>
 * </ul>
 *
 * <p>Consultar: el personal de soporte ve todas (bandeja, de la más antigua a la más nueva); el
 * CLIENTE_ADMIN, las de su empresa. Una solicitud de otra empresa responde 404.
 *
 * <p>Programar y rechazar ({@code SOLICITUDES_GESTIONAR}): solo desde PENDIENTE. Programar crea la
 * capacitación y marca la solicitud en una transacción; exige que la empresa tenga soporte (decidido
 * en 2.8). Las dos escriben solo si la solicitud sigue como se leyó: si otro agente la atendió en
 * medio, 409 y no queda una capacitación huérfana.
 *
 * <p>Eliminar: nunca una PROGRAMADA (422). Para liberarla, el administrador elimina su capacitación
 * (HU36), que la devuelve a PENDIENTE.
 */
@Service
public class SolicitudCapacitacionService {

    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaCreacion");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> TODOS_LOS_ESTADOS = List.of(EstadoSolicitud.PENDIENTE.name(),
            EstadoSolicitud.PROGRAMADA.name(), EstadoSolicitud.RECHAZADA.name());

    private final SolicitudCapacitacionRepository repositorio;
    private final MongoTemplate template;
    private final CapacitacionService capacitaciones;
    private final SoporteService soporte;
    private final MembresiaService membresias;
    private final NombresUsuarioService nombresUsuarios;
    private final NombresEmpresaService nombresEmpresas;
    private final TransaccionConReintento transacciones;
    private final Clock reloj;

    public SolicitudCapacitacionService(SolicitudCapacitacionRepository repositorio, MongoTemplate template,
            CapacitacionService capacitaciones, SoporteService soporte, MembresiaService membresias,
            NombresUsuarioService nombresUsuarios, NombresEmpresaService nombresEmpresas,
            TransaccionConReintento transacciones, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.capacitaciones = capacitaciones;
        this.soporte = soporte;
        this.membresias = membresias;
        this.nombresUsuarios = nombresUsuarios;
        this.nombresEmpresas = nombresEmpresas;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    public SolicitudResponse crear(CrearSolicitudRequest solicitud, UsuarioAutenticado actor) {
        if (!actor.esDeEmpresa()) {
            throw new ReglaNegocioException("Solo un usuario de una empresa cliente registra solicitudes de "
                    + "capacitación.");
        }
        soporte.exigirSoporte(actor);
        ContratoEmpresa contrato = membresias.contratoDeEmpresa(actor.empresaId())
                .orElseThrow(() -> new ReglaNegocioException("La membresía de la empresa no tiene un plan vigente."));
        ReglasCapacitacion.verificarModuloContratado(solicitud.modulo(), contrato);
        Instant ahora = ahora();
        if (solicitud.fechaPreferida() != null
                && solicitud.fechaPreferida().isBefore(LocalDate.ofInstant(ahora, CapacitacionMapper.BOGOTA))) {
            throw new ReglaNegocioException("La fecha preferida no puede ser anterior a hoy.");
        }
        verificarLimiteDelPlan(actor.empresaId(), contrato);

        SolicitudCapacitacion nueva = new SolicitudCapacitacion();
        nueva.setFechaCreacion(ahora);
        nueva.setFechaActualizacion(ahora);
        nueva.setEmpresaId(actor.empresaId());
        nueva.setSolicitanteId(actor.id());
        nueva.setModulo(solicitud.modulo());
        nueva.setDescripcion(solicitud.descripcion().strip());
        nueva.setParticipantesEstimados(solicitud.participantesEstimados());
        nueva.setFechaPreferida(CapacitacionMapper.inicioDelDia(solicitud.fechaPreferida()));
        verificarCoherencia(nueva);
        return respuesta(repositorio.insert(nueva));
    }

    /**
     * Solicitudes PENDIENTE o PROGRAMADA creadas en el mes pagado vigente, por
     * {@code ix_solicitudes_empresa_fecha}. Dos solicitudes simultáneas podrían pasar las dos la
     * verificación; con el volumen de este sistema se acepta, igual que {@code maxUsuarios} (2.5).
     */
    private void verificarLimiteDelPlan(ObjectId empresaId, ContratoEmpresa contrato) {
        int incluidas = contrato.plan().limites().capacitacionesIncluidas();
        if (incluidas == 0) {
            throw new ReglaNegocioException("El plan " + contrato.plan().nombre()
                    + " no incluye capacitaciones personalizadas.");
        }
        Instant desde = ReglasCapacitacion.inicioMesPagado(contrato);
        long usadas = template.count(new Query(Criteria.where("empresaId").is(empresaId)
                .and("fechaCreacion").gte(desde)
                .and("estado").in(EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.PROGRAMADA.name())),
                SolicitudCapacitacion.class);
        if (usadas >= incluidas) {
            throw new ReglaNegocioException("El plan " + contrato.plan().nombre() + " incluye " + incluidas
                    + " capacitaciones personalizadas por mes de vigencia y la empresa ya tiene " + usadas
                    + " pendientes o programadas desde el " + DIA.format(desde.atZone(CapacitacionMapper.BOGOTA))
                    + ".");
        }
    }

    // ------------------------------------------------------------------
    // Consultar
    // ------------------------------------------------------------------

    /**
     * Personal de soporte: {@code ?estado=PENDIENTE} es la bandeja, por {@code ix_solicitudes_estado_fecha},
     * de la más antigua a la más nueva. Sin estado, la consulta lleva los tres para usar el mismo índice.
     * CLIENTE_ADMIN: las de su empresa, de la más nueva a la más antigua, por {@code ix_solicitudes_empresa_fecha}.
     */
    public Pagina<SolicitudResponse> listar(EstadoSolicitud estado, String empresaId, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        boolean deEmpresa = actor.esDeEmpresa();
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(deEmpresa ? Sort.Direction.DESC : Sort.Direction.ASC, "fechaCreacion"));

        Query consulta = new Query();
        ObjectId empresa = deEmpresa ? actor.empresaId() : Ids.deParametro("empresaId", empresaId);
        if (empresa != null) {
            consulta.addCriteria(Criteria.where("empresaId").is(empresa));
        }
        if (estado != null) {
            consulta.addCriteria(Criteria.where("estado").is(estado.name()));
        } else if (empresa == null) {
            consulta.addCriteria(Criteria.where("estado").in(TODOS_LOS_ESTADOS));
        }

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(consulta, SolicitudCapacitacion.class);
        List<SolicitudCapacitacion> solicitudes = template.find(consulta.with(conOrden), SolicitudCapacitacion.class);
        NombresCapacitacion nombres = nombres(solicitudes);
        return Pagina.de(new PageImpl<>(solicitudes, conOrden, total), s -> CapacitacionMapper.aRespuesta(s, nombres));
    }

    public SolicitudResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        return respuesta(buscarVisible(id, actor));
    }

    // ------------------------------------------------------------------
    // Programar (HU33) y rechazar
    // ------------------------------------------------------------------

    /**
     * En una transacción: inserta la capacitación PERSONALIZADA y pasa la solicitud a PROGRAMADA. Si la
     * solicitud cambió desde que se leyó, 409 y la capacitación no queda.
     */
    public CapacitacionResponse programar(ObjectId id, ProgramarSolicitudRequest datos, UsuarioAutenticado actor) {
        return transacciones.ejecutar(() -> {
            SolicitudCapacitacion solicitud = buscarVisible(id, actor);
            exigirPendiente(solicitud, "programar");
            capacitaciones.verificarEmpresaPersonalizada(solicitud.getEmpresaId(), solicitud.getModulo());

            Instant ahora = ahora();
            Capacitacion capacitacion = capacitaciones.insertarDesdeSolicitud(solicitud, datos, ahora);
            Instant leida = solicitud.getFechaActualizacion();
            solicitud.programar(capacitacion.getId(), actor.id());
            solicitud.setFechaActualizacion(ahora);
            guardar(solicitud, leida);
            return capacitaciones.detalle(capacitacion, actor);
        });
    }

    public SolicitudResponse rechazar(ObjectId id, RechazarSolicitudRequest datos, UsuarioAutenticado actor) {
        SolicitudCapacitacion solicitud = buscarVisible(id, actor);
        exigirPendiente(solicitud, "rechazar");
        Instant leida = solicitud.getFechaActualizacion();
        solicitud.rechazar(datos.motivo().strip(), actor.id());
        solicitud.setFechaActualizacion(ahora());
        return respuesta(guardar(solicitud, leida));
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    /**
     * Borrado físico. La condición {@code estado != PROGRAMADA} va en el mismo {@code deleteOne}, así una
     * solicitud que otro agente programó en medio no se borra.
     */
    public void eliminar(ObjectId id, UsuarioAutenticado actor) {
        SolicitudCapacitacion solicitud = buscarVisible(id, actor);
        if (solicitud.getEstado() == EstadoSolicitud.PROGRAMADA) {
            throw programadaNoSeElimina();
        }
        long borradas = template.remove(new Query(Criteria.where("_id").is(id)
                .and("estado").ne(EstadoSolicitud.PROGRAMADA.name())), SolicitudCapacitacion.class).getDeletedCount();
        if (borradas == 0) {
            throw programadaNoSeElimina();
        }
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /** La solicitud si el actor puede verla; un usuario de empresa, solo las de la suya. Si no, 404. */
    private SolicitudCapacitacion buscarVisible(ObjectId id, UsuarioAutenticado actor) {
        SolicitudCapacitacion solicitud = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud de capacitación."));
        if (actor.esDeEmpresa() && !solicitud.getEmpresaId().equals(actor.empresaId())) {
            throw new RecursoNoEncontradoException("No existe la solicitud de capacitación.");
        }
        return solicitud;
    }

    private static void exigirPendiente(SolicitudCapacitacion solicitud, String accion) {
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ReglaNegocioException("Solo se puede " + accion + " una solicitud pendiente. Esta está "
                    + solicitud.getEstado() + ".");
        }
    }

    private static ReglaNegocioException programadaNoSeElimina() {
        return new ReglaNegocioException("La solicitud ya está programada y no se puede eliminar.");
    }

    /**
     * Reemplaza la solicitud solo si sigue PENDIENTE y con la {@code fechaActualizacion} leída. Si otro
     * agente la atendió en medio, 409 (dentro de una transacción, la aborta).
     */
    private SolicitudCapacitacion guardar(SolicitudCapacitacion solicitud, Instant leida) {
        verificarCoherencia(solicitud);
        Query mismaVersion = new Query(Criteria.where("_id").is(solicitud.getId())
                .and("estado").is(EstadoSolicitud.PENDIENTE.name())
                .and("fechaActualizacion").is(leida));
        if (template.findAndReplace(mismaVersion, solicitud) == null) {
            throw new ApiException(CodigoError.CONFLICTO,
                    "Otra persona atendió la solicitud mientras usted la revisaba. Recárguela e intente de nuevo.");
        }
        return solicitud;
    }

    /** Red de seguridad del {@code oneOf} por estado (2.3). */
    private static void verificarCoherencia(SolicitudCapacitacion solicitud) {
        if (!solicitud.estadoCoherente()) {
            throw new ReglaNegocioException("Los datos de la solicitud no corresponden a su estado.");
        }
    }

    private SolicitudResponse respuesta(SolicitudCapacitacion solicitud) {
        return CapacitacionMapper.aRespuesta(solicitud, nombres(List.of(solicitud)));
    }

    private NombresCapacitacion nombres(Collection<SolicitudCapacitacion> solicitudes) {
        return new NombresCapacitacion(
                nombresUsuarios.nombres(CapacitacionMapper.idsDeUsuariosDeSolicitudes(solicitudes)),
                nombresEmpresas.nombres(CapacitacionMapper.idsDeEmpresasDeSolicitudes(solicitudes)));
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
