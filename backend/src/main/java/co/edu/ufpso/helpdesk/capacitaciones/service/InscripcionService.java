package co.edu.ufpso.helpdesk.capacitaciones.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.dto.InscribirRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.InscripcionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.MarcacionAsistenciaResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.MarcarAsistenciasRequest;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.CapacitacionMapper;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.NombresCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.repository.CapacitacionRepository;
import co.edu.ufpso.helpdesk.capacitaciones.repository.InscripcionRepository;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.ErrorCampo;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.common.transaccion.TransaccionConReintento;
import co.edu.ufpso.helpdesk.empresas.service.NombresEmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.PermisosEfectivos;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;

/**
 * Inscripciones y asistencias (HU37 a HU40, modelo 1.2 sección 12, tarea 2.8).
 *
 * <p>Inscribir (HU37):
 * <ul>
 *   <li>Con {@code INSCRIPCIONES_PROPIAS} el usuario se inscribe a sí mismo, y solo en masivas. En las
 *       personalizadas inscribe el personal de soporte ({@code INSCRIPCIONES_GESTIONAR}), indicando el
 *       usuario, que debe ser de la empresa de la capacitación.</li>
 *   <li>El inscrito es un usuario de empresa activo con {@code INSCRIPCIONES_PROPIAS} en un rol activo.
 *       Así un CLIENTE_ADMIN sin el rol CLIENTE no se inscribe (contexto 8.4).</li>
 *   <li>Soporte: quien se inscribe con {@code exigirSoporte}; el agente, con {@code exigirSoporteEmpresa}
 *       de la empresa del usuario (2.5).</li>
 *   <li>Solo capacitaciones PROGRAMADAS que todavía no empiezan, la misma fecha que usa HU40 para
 *       cancelar (decidido en 2.8). Una vez por usuario: 409 {@code DUPLICADO}.</li>
 *   <li>Transacción: {@code findAndModify} sobre la capacitación con la condición
 *       {@code cuposOcupados < cupo} y {@code $inc: 1}, y luego el insert de la inscripción. Si dos
 *       personas toman el último cupo a la vez, MongoDB aborta una con {@code WriteConflict};
 *       {@link TransaccionConReintento} la repite, ve el cupo lleno y responde 422.</li>
 * </ul>
 *
 * <p>Consultar (HU38): la lista de inscritos de una capacitación ({@code INSCRIPCIONES_VER}) y "mis
 * inscripciones" ({@code INSCRIPCIONES_PROPIAS}).
 *
 * <p>Asistencia (HU39): la marca el instructor de la capacitación o quien tiene
 * {@code CAPACITACIONES_ELIMINAR} (administrador; decidido en 2.8), desde la hora de inicio y nunca en
 * una cancelada. Se puede volver a marcar.
 *
 * <p>Cancelar (HU40): el usuario cancela la suya antes de la hora de inicio; con
 * {@code INSCRIPCIONES_GESTIONAR}, cualquiera que no tenga la asistencia marcada (para no borrar
 * historial de A6 y A9). Borrado físico y {@code $inc: -1} en una transacción.
 */
@Service
public class InscripcionService {

    /** Lista de inscritos: el orden lo da {@code ux_inscripciones_capacitacion_usuario}, no se elige. */
    private static final Set<String> ORDEN_INSCRITOS = Set.of();
    /** Mis inscripciones: {@code ix_inscripciones_usuario_fecha}. */
    private static final Set<String> ORDEN_MIAS = Set.of("fechaCreacion");
    /** Campos de la capacitación que muestran las inscripciones. */
    private static final String[] CAMPOS_CAPACITACION = { "titulo", "modulo", "fechaInicio", "fechaFin", "estado" };

    private final InscripcionRepository repositorio;
    private final CapacitacionRepository capacitacionesRepositorio;
    private final CapacitacionService capacitaciones;
    private final MongoTemplate template;
    private final SoporteService soporte;
    private final PermisosEfectivos permisos;
    private final NombresUsuarioService nombresUsuarios;
    private final NombresEmpresaService nombresEmpresas;
    private final TransaccionConReintento transacciones;
    private final Clock reloj;

    public InscripcionService(InscripcionRepository repositorio, CapacitacionRepository capacitacionesRepositorio,
            CapacitacionService capacitaciones, MongoTemplate template, SoporteService soporte,
            PermisosEfectivos permisos, NombresUsuarioService nombresUsuarios, NombresEmpresaService nombresEmpresas,
            TransaccionConReintento transacciones, Clock reloj) {
        this.repositorio = repositorio;
        this.capacitacionesRepositorio = capacitacionesRepositorio;
        this.capacitaciones = capacitaciones;
        this.template = template;
        this.soporte = soporte;
        this.permisos = permisos;
        this.nombresUsuarios = nombresUsuarios;
        this.nombresEmpresas = nombresEmpresas;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU37 · Inscribir
    // ------------------------------------------------------------------

    public InscripcionResponse inscribir(ObjectId capacitacionId, InscribirRequest solicitud, UsuarioAutenticado actor) {
        ObjectId pedido = solicitud == null || solicitud.usuarioId() == null ? null : Ids.deCuerpo(solicitud.usuarioId());
        boolean paraOtro = pedido != null && !pedido.equals(actor.id());

        UsuarioAutenticado inscrito;
        Capacitacion capacitacion;
        if (paraOtro) {
            if (!actor.tiene(Permiso.INSCRIPCIONES_GESTIONAR)) {
                throw new ApiException(CodigoError.ACCESO_DENEGADO, "Solo puede inscribirse a usted mismo.");
            }
            inscrito = inscribible(pedido);
            if (actor.esDeEmpresa() && !inscrito.empresaId().equals(actor.empresaId())) {
                throw new ReglaNegocioException("Solo puede inscribir a usuarios de su empresa.");
            }
            capacitacion = capacitaciones.buscarVisible(capacitacionId, actor);
            if (capacitacion.getTipo() == TipoCapacitacion.PERSONALIZADA
                    && !capacitacion.getEmpresaId().equals(inscrito.empresaId())) {
                throw new ReglaNegocioException("El usuario no pertenece a la empresa de esta capacitación "
                        + "personalizada.");
            }
            soporte.exigirSoporteEmpresa(inscrito.empresaId());
        } else {
            if (!actor.tiene(Permiso.INSCRIPCIONES_PROPIAS)) {
                // Tiene INSCRIPCIONES_GESTIONAR (si no, @PreAuthorize no lo habría dejado pasar).
                throw new ApiException(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                        Map.of("errores", List.of(new ErrorCampo("usuarioId", "Indique el usuario a inscribir."))));
            }
            soporte.exigirSoporte(actor);
            if (!actor.esDeEmpresa()) {
                throw new ReglaNegocioException("Solo los usuarios de las empresas cliente se inscriben a capacitaciones.");
            }
            inscrito = actor;
            capacitacion = capacitaciones.buscarVisible(capacitacionId, actor);
            if (capacitacion.getTipo() == TipoCapacitacion.PERSONALIZADA) {
                throw new ReglaNegocioException("En las capacitaciones personalizadas, el agente de soporte inscribe "
                        + "a los usuarios de la empresa.");
            }
        }
        // Las reglas se revisan antes para dar el motivo exacto; la transacción las vuelve a exigir.
        verificarAdmiteInscripciones(capacitacion, ahora());
        if (repositorio.existsByCapacitacionIdAndUsuarioId(capacitacionId, inscrito.id())) {
            throw yaInscrito();
        }

        Inscripcion creada = transacciones.ejecutar(() -> {
            Instant ahora = ahora();
            Query conCupo = new Query(Criteria.where("_id").is(capacitacionId)
                    .and("estado").is(EstadoCapacitacion.PROGRAMADA.name())
                    .and("fechaInicio").gt(ahora))
                    .addCriteria(Criteria.expr(ComparisonOperators.valueOf("cuposOcupados").lessThan("cupo")));
            Capacitacion tomada = template.findAndModify(conCupo, new Update().inc("cuposOcupados", 1),
                    FindAndModifyOptions.options().returnNew(true), Capacitacion.class);
            if (tomada == null) {
                Capacitacion actual = capacitacionesRepositorio.findById(capacitacionId)
                        .orElseThrow(CapacitacionService::noExiste);
                verificarAdmiteInscripciones(actual, ahora);
                throw new ReglaNegocioException("La capacitación no admite inscripciones.");
            }
            if (repositorio.existsByCapacitacionIdAndUsuarioId(capacitacionId, inscrito.id())) {
                // Aborta la transacción: el cupo que se acaba de tomar vuelve.
                throw yaInscrito();
            }
            Inscripcion nueva = new Inscripcion();
            nueva.setFechaCreacion(ahora);
            nueva.setFechaActualizacion(ahora);
            nueva.setCapacitacionId(capacitacionId);
            nueva.setUsuarioId(inscrito.id());
            nueva.setEmpresaId(inscrito.empresaId());
            // Si otra petición insertó la misma inscripción entre la lectura y aquí, el índice único
            // responde 409 DUPLICADO y la transacción completa se deshace.
            return repositorio.insert(nueva);
        });
        return respuesta(creada);
    }

    /** Usuario activo, de empresa, con {@code INSCRIPCIONES_PROPIAS} en un rol activo. Si no, 422. */
    private UsuarioAutenticado inscribible(ObjectId usuarioId) {
        return permisos.resolver(usuarioId)
                .filter(u -> u.esDeEmpresa() && u.tiene(Permiso.INSCRIPCIONES_PROPIAS))
                .orElseThrow(() -> new ReglaNegocioException("El usuario debe estar activo, pertenecer a una empresa "
                        + "cliente y poder inscribirse a capacitaciones."));
    }

    /** PROGRAMADA, sin empezar y con cupo. Si no, 422 con el motivo. */
    private static void verificarAdmiteInscripciones(Capacitacion capacitacion, Instant ahora) {
        if (capacitacion.getEstado() == EstadoCapacitacion.CANCELADA) {
            throw new ReglaNegocioException("La capacitación está cancelada.");
        }
        if (capacitacion.empezo(ahora)) {
            throw new ReglaNegocioException("La capacitación ya empezó o ya se realizó; no admite inscripciones.");
        }
        if (capacitacion.cuposDisponibles() <= 0) {
            throw new ReglaNegocioException("La capacitación no tiene cupos disponibles.");
        }
    }

    private static ConflictoException yaInscrito() {
        return ConflictoException.duplicado("El usuario ya está inscrito en esta capacitación.");
    }

    // ------------------------------------------------------------------
    // HU38 · Consultar
    // ------------------------------------------------------------------

    /** Inscritos de una capacitación, en el orden de {@code ux_inscripciones_capacitacion_usuario}. */
    public Pagina<InscripcionResponse> listarDeCapacitacion(ObjectId capacitacionId, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_INSCRITOS);
        capacitaciones.buscarVisible(capacitacionId, actor);
        Pageable conOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("usuarioId"));
        return pagina(new Query(Criteria.where("capacitacionId").is(capacitacionId)), conOrden);
    }

    /** Mis inscripciones (HU38), de la más reciente a la más antigua por {@code ix_inscripciones_usuario_fecha}. */
    public Pagina<InscripcionResponse> mias(Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_MIAS);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return pagina(new Query(Criteria.where("usuarioId").is(actor.id())), conOrden);
    }

    private Pagina<InscripcionResponse> pagina(Query consulta, Pageable conOrden) {
        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(consulta, Inscripcion.class);
        List<Inscripcion> inscripciones = template.find(consulta.with(conOrden), Inscripcion.class);
        Map<ObjectId, Capacitacion> sesiones = capacitacionesDe(inscripciones);
        NombresCapacitacion nombres = nombres(inscripciones);
        Instant ahora = ahora();
        return Pagina.de(new PageImpl<>(inscripciones, conOrden, total),
                i -> CapacitacionMapper.aRespuesta(i, sesiones.get(i.getCapacitacionId()), nombres, ahora));
    }

    // ------------------------------------------------------------------
    // HU39 · Marcar asistencia
    // ------------------------------------------------------------------

    public InscripcionResponse marcar(ObjectId inscripcionId, boolean asistio, UsuarioAutenticado actor) {
        Inscripcion inscripcion = repositorio.findById(inscripcionId).orElseThrow(InscripcionService::noExiste);
        Capacitacion capacitacion = capacitacionesRepositorio.findById(inscripcion.getCapacitacionId())
                .orElseThrow(InscripcionService::noExiste);
        Instant ahora = ahora();
        verificarPuedeMarcar(capacitacion, actor, ahora);

        inscripcion.marcarAsistencia(asistio, actor.id(), ahora);
        inscripcion.setFechaActualizacion(ahora);
        if (!inscripcion.marcaCoherente()) {
            throw new ReglaNegocioException("La marca de asistencia está incompleta.");
        }
        long marcadas = template.updateFirst(new Query(Criteria.where("_id").is(inscripcionId)), marca(inscripcion),
                Inscripcion.class).getMatchedCount();
        if (marcadas == 0) {
            // La cancelaron mientras se marcaba.
            throw noExiste();
        }
        return respuesta(inscripcion);
    }

    /**
     * Toda la lista en una petición, con un {@code bulkWrite}. Antes se comprueba que todas las
     * inscripciones sean de la capacitación, así no queda la mitad marcada por un id equivocado.
     */
    public MarcacionAsistenciaResponse marcarVarias(ObjectId capacitacionId, MarcarAsistenciasRequest solicitud,
            UsuarioAutenticado actor) {
        Capacitacion capacitacion = capacitaciones.buscarVisible(capacitacionId, actor);
        Instant ahora = ahora();
        verificarPuedeMarcar(capacitacion, actor, ahora);

        Map<ObjectId, Boolean> marcas = new HashMap<>();
        for (MarcarAsistenciasRequest.Marca m : solicitud.marcas()) {
            if (marcas.put(Ids.deCuerpo(m.inscripcionId()), m.asistio()) != null) {
                throw new ReglaNegocioException("La inscripción " + m.inscripcionId().strip() + " viene repetida.");
            }
        }
        long deEsta = template.count(new Query(Criteria.where("_id").in(marcas.keySet())
                .and("capacitacionId").is(capacitacionId)), Inscripcion.class);
        if (deEsta != marcas.size()) {
            throw new ReglaNegocioException("Algunas inscripciones no existen o no son de esta capacitación.");
        }

        BulkOperations lote = template.bulkOps(BulkOperations.BulkMode.UNORDERED, Inscripcion.class);
        marcas.forEach((id, asistio) -> lote.updateOne(
                new Query(Criteria.where("_id").is(id).and("capacitacionId").is(capacitacionId)),
                new Update().set("asistio", asistio).set("marcadoPorId", actor.id()).set("fechaMarcado", ahora)
                        .set("fechaActualizacion", ahora)));
        return new MarcacionAsistenciaResponse(lote.execute().getMatchedCount());
    }

    /**
     * Instructor de la capacitación o administrador ({@code CAPACITACIONES_ELIMINAR}, decidido en 2.8);
     * desde la hora de inicio y nunca en una cancelada.
     */
    private static void verificarPuedeMarcar(Capacitacion capacitacion, UsuarioAutenticado actor, Instant ahora) {
        boolean esInstructor = actor.id().equals(capacitacion.getInstructorId());
        if (!esInstructor && !actor.tiene(Permiso.CAPACITACIONES_ELIMINAR)) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "Solo el instructor de la capacitación o un administrador marcan la asistencia.");
        }
        if (capacitacion.getEstado() == EstadoCapacitacion.CANCELADA) {
            throw new ReglaNegocioException("La capacitación está cancelada; no se marca asistencia.");
        }
        if (!capacitacion.empezo(ahora)) {
            throw new ReglaNegocioException("La asistencia se marca a partir de la hora de inicio de la capacitación.");
        }
    }

    private static Update marca(Inscripcion i) {
        return new Update().set("asistio", i.getAsistio()).set("marcadoPorId", i.getMarcadoPorId())
                .set("fechaMarcado", i.getFechaMarcado()).set("fechaActualizacion", i.getFechaActualizacion());
    }

    // ------------------------------------------------------------------
    // HU40 · Cancelar inscripción
    // ------------------------------------------------------------------

    public void cancelar(ObjectId inscripcionId, UsuarioAutenticado actor) {
        Inscripcion inscripcion = repositorio.findById(inscripcionId).orElseThrow(InscripcionService::noExiste);
        boolean propia = inscripcion.getUsuarioId().equals(actor.id());
        if (actor.tiene(Permiso.INSCRIPCIONES_GESTIONAR)) {
            if (actor.esDeEmpresa() && !inscripcion.getEmpresaId().equals(actor.empresaId())) {
                throw noExiste();
            }
            if (inscripcion.getAsistio() != null) {
                throw new ReglaNegocioException("La inscripción ya tiene la asistencia marcada; no se cancela para "
                        + "conservar el historial.");
            }
        } else {
            if (!propia) {
                // Una inscripción ajena responde igual que una que no existe (IDOR).
                throw noExiste();
            }
            Capacitacion capacitacion = capacitacionesRepositorio.findById(inscripcion.getCapacitacionId())
                    .orElseThrow(InscripcionService::noExiste);
            if (capacitacion.empezo(ahora())) {
                throw new ReglaNegocioException("Solo puede cancelar su inscripción antes de la fecha de la "
                        + "capacitación.");
            }
        }

        transacciones.ejecutar(() -> {
            // Pasos del modelo 1.2: borrar la inscripción y liberar el cupo. La condición asistio null
            // evita borrar una que el instructor marcó en medio.
            long borradas = template.remove(new Query(Criteria.where("_id").is(inscripcionId).and("asistio").is(null)),
                    Inscripcion.class).getDeletedCount();
            if (borradas == 0) {
                if (repositorio.existsById(inscripcionId)) {
                    throw new ReglaNegocioException("La inscripción ya tiene la asistencia marcada; no se cancela.");
                }
                throw noExiste();
            }
            template.updateFirst(new Query(Criteria.where("_id").is(inscripcion.getCapacitacionId())
                    .and("cuposOcupados").gt(0)), new Update().inc("cuposOcupados", -1), Capacitacion.class);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private InscripcionResponse respuesta(Inscripcion inscripcion) {
        List<Inscripcion> una = List.of(inscripcion);
        return CapacitacionMapper.aRespuesta(inscripcion, capacitacionesDe(una).get(inscripcion.getCapacitacionId()),
                nombres(una), ahora());
    }

    /** Las capacitaciones de una página, con proyección, en una sola consulta por {@code _id}. */
    private Map<ObjectId, Capacitacion> capacitacionesDe(Collection<Inscripcion> inscripciones) {
        Set<ObjectId> ids = new HashSet<>();
        inscripciones.forEach(i -> ids.add(i.getCapacitacionId()));
        Map<ObjectId, Capacitacion> sesiones = new HashMap<>();
        if (ids.isEmpty()) {
            return sesiones;
        }
        Query consulta = new Query(Criteria.where("_id").in(ids));
        consulta.fields().include(CAMPOS_CAPACITACION);
        for (Capacitacion c : template.find(consulta, Capacitacion.class)) {
            sesiones.put(c.getId(), c);
        }
        return sesiones;
    }

    private NombresCapacitacion nombres(Collection<Inscripcion> inscripciones) {
        return new NombresCapacitacion(
                nombresUsuarios.nombres(CapacitacionMapper.idsDeUsuariosDeInscripciones(inscripciones)),
                nombresEmpresas.nombres(CapacitacionMapper.idsDeEmpresasDeInscripciones(inscripciones)));
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe la inscripción solicitada.");
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
