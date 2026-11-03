package co.edu.ufpso.helpdesk.capacitaciones.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoSolicitud;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.dto.ActualizarCapacitacionRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CrearCapacitacionRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.EliminacionCapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.FiltroCapacitaciones;
import co.edu.ufpso.helpdesk.capacitaciones.dto.ProgramarSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.CapacitacionMapper;
import co.edu.ufpso.helpdesk.capacitaciones.mapper.NombresCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.repository.CapacitacionRepository;
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
import co.edu.ufpso.helpdesk.common.transaccion.TransaccionConReintento;
import co.edu.ufpso.helpdesk.empresas.service.NombresEmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.PermisosEfectivos;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;

/**
 * Capacitaciones (HU33 a HU36, modelo 1.2 sección 10, tarea 2.8).
 *
 * <p>Crear (HU33):
 * <ul>
 *   <li>Fecha y hora de inicio posteriores a la hora actual; cupo mayor que cero; duración de 1 a 1440
 *       minutos.</li>
 *   <li>Instructor: usuario activo de alcance GLOBAL con {@code ASISTENCIA_MARCAR} en un rol activo.
 *       Si no puede marcar la asistencia (HU39), no puede dictar la sesión.</li>
 *   <li>MASIVA: sin empresa. PERSONALIZADA: la empresa existe, tiene soporte (decidido en 2.8, misma
 *       regla de 2.5 para un agente que actúa sobre una empresa) y tiene contratado el módulo. Una
 *       personalizada creada aquí no tiene solicitud; la que nace de una solicitud la crea
 *       {@link SolicitudCapacitacionService#programar}.</li>
 * </ul>
 *
 * <p>Consultar (HU34): el personal de soporte ve todas; un usuario de empresa ve las masivas y las
 * personalizadas de su empresa. Una personalizada de otra empresa responde 404 (IDOR). El cronograma
 * se ordena solo por {@code fechaInicio}.
 *
 * <p>Actualizar (HU35): título, fecha, duración, cupo, instructor y cancelar. Una capacitación
 * cancelada o ya realizada no se modifica (422). La cancelación es definitiva (decidido en 2.8). El
 * cupo no queda por debajo de los inscritos: la escritura lleva la condición
 * {@code cuposOcupados <= cupo}, así una inscripción simultánea no la deja inconsistente.
 *
 * <p>Eliminar (HU36): en una transacción se borran las inscripciones, la solicitud de origen vuelve
 * a PENDIENTE (HU36 v.4) y se borra la capacitación.
 */
@Service
public class CapacitacionService {

    /** El cronograma se ordena por la fecha de la sesión: primer campo de {@code ix_capacitaciones_fecha_modulo}. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaInicio");

    private final CapacitacionRepository repositorio;
    private final MongoTemplate template;
    private final SoporteService soporte;
    private final MembresiaService membresias;
    private final PermisosEfectivos permisos;
    private final NombresUsuarioService nombresUsuarios;
    private final NombresEmpresaService nombresEmpresas;
    private final TransaccionConReintento transacciones;
    private final Clock reloj;

    public CapacitacionService(CapacitacionRepository repositorio, MongoTemplate template, SoporteService soporte,
            MembresiaService membresias, PermisosEfectivos permisos, NombresUsuarioService nombresUsuarios,
            NombresEmpresaService nombresEmpresas, TransaccionConReintento transacciones, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.soporte = soporte;
        this.membresias = membresias;
        this.permisos = permisos;
        this.nombresUsuarios = nombresUsuarios;
        this.nombresEmpresas = nombresEmpresas;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU34 · Consultar
    // ------------------------------------------------------------------

    /**
     * Cronograma paginado. Ejemplo: {@code ?modulo=INVENTARIOS&desde=2026-11-01&hasta=2026-11-30}.
     * Para un usuario de empresa la consulta lleva {@code empresaId: {$in: [null, la suya]}} y MongoDB
     * mezcla los dos tramos de {@code ix_capacitaciones_empresa_fecha} ya ordenados por fecha.
     */
    public Pagina<CapacitacionResponse> listar(FiltroCapacitaciones filtro, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.ASC, "fechaInicio"));

        Query consulta = new Query();
        if (actor.esDeEmpresa()) {
            consulta.addCriteria(Criteria.where("empresaId").in(Arrays.asList(null, actor.empresaId())));
        } else {
            ObjectId empresa = Ids.deParametro("empresaId", filtro.empresaId());
            if (empresa != null) {
                consulta.addCriteria(Criteria.where("empresaId").is(empresa));
            }
        }
        if (filtro.modulo() != null) {
            consulta.addCriteria(Criteria.where("modulo").is(filtro.modulo()));
        }
        if (filtro.tipo() != null) {
            consulta.addCriteria(Criteria.where("tipo").is(filtro.tipo()));
        }
        if (filtro.estado() != null) {
            consulta.addCriteria(Criteria.where("estado").is(filtro.estado()));
        }
        if (filtro.desde() != null || filtro.hasta() != null) {
            if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
                throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                        "La fecha 'desde' no puede ser posterior a 'hasta'.");
            }
            Criteria fecha = Criteria.where("fechaInicio");
            if (filtro.desde() != null) {
                fecha = fecha.gte(CapacitacionMapper.inicioDelDia(filtro.desde()));
            }
            if (filtro.hasta() != null) {
                // Hasta el final del día: menor que el comienzo del día siguiente.
                fecha = fecha.lt(CapacitacionMapper.inicioDelDia(filtro.hasta().plusDays(1)));
            }
            consulta.addCriteria(fecha);
        }

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(consulta, Capacitacion.class);
        List<Capacitacion> capacitaciones = template.find(consulta.with(conOrden), Capacitacion.class);
        NombresCapacitacion nombres = nombres(capacitaciones);
        Map<ObjectId, ObjectId> mias = misInscripciones(capacitaciones, actor);
        Instant ahora = ahora();
        return Pagina.de(new PageImpl<>(capacitaciones, conOrden, total),
                c -> CapacitacionMapper.aRespuesta(c, nombres, ahora, null, mias.get(c.getId())));
    }

    public CapacitacionResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        return detalle(buscarVisible(id, actor), actor);
    }

    // ------------------------------------------------------------------
    // HU33 · Crear
    // ------------------------------------------------------------------

    public CapacitacionResponse crear(CrearCapacitacionRequest solicitud, UsuarioAutenticado actor) {
        ObjectId empresaId = null;
        if (solicitud.tipo() == TipoCapacitacion.MASIVA) {
            if (solicitud.empresaId() != null) {
                throw new ReglaNegocioException("Una capacitación masiva no lleva empresa: queda abierta a todas "
                        + "las empresas con soporte.");
            }
        } else {
            if (solicitud.empresaId() == null) {
                throw new ApiException(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                        Map.of("errores", List.of(new ErrorCampo("empresaId",
                                "Indique la empresa de la capacitación personalizada."))));
            }
            empresaId = Ids.deCuerpo(solicitud.empresaId());
            verificarEmpresaPersonalizada(empresaId, solicitud.modulo());
        }

        Instant ahora = ahora();
        Capacitacion capacitacion = nueva(solicitud.titulo(), solicitud.fechaInicio(), solicitud.duracionMinutos(),
                solicitud.cupo(), Ids.deCuerpo(solicitud.instructorId()), ahora);
        capacitacion.setTipo(solicitud.tipo());
        capacitacion.setModulo(solicitud.modulo());
        capacitacion.setEmpresaId(empresaId);
        capacitacion.setSolicitudId(null);
        verificarCoherencia(capacitacion);
        return detalle(repositorio.insert(capacitacion), actor);
    }

    /**
     * HU33 desde una solicitud: arma e inserta la PERSONALIZADA con la empresa y el módulo de la
     * solicitud. Debe correr dentro de la transacción de {@link SolicitudCapacitacionService#programar},
     * que antes revisó el soporte y el módulo contratado.
     */
    Capacitacion insertarDesdeSolicitud(SolicitudCapacitacion origen, ProgramarSolicitudRequest datos, Instant ahora) {
        Capacitacion capacitacion = nueva(datos.titulo(), datos.fechaInicio(), datos.duracionMinutos(), datos.cupo(),
                Ids.deCuerpo(datos.instructorId()), ahora);
        capacitacion.setTipo(TipoCapacitacion.PERSONALIZADA);
        capacitacion.setModulo(origen.getModulo());
        capacitacion.setEmpresaId(origen.getEmpresaId());
        capacitacion.setSolicitudId(origen.getId());
        verificarCoherencia(capacitacion);
        return repositorio.insert(capacitacion);
    }

    /** Campos comunes de una sesión nueva, con sus reglas: inicio futuro e instructor válido. */
    private Capacitacion nueva(String titulo, Instant fechaInicio, int duracionMinutos, int cupo,
            ObjectId instructorId, Instant ahora) {
        Instant inicio = fechaInicio.truncatedTo(ChronoUnit.MILLIS);
        verificarInicioFuturo(inicio, ahora);
        verificarInstructor(instructorId);
        Capacitacion c = new Capacitacion();
        c.setFechaCreacion(ahora);
        c.setFechaActualizacion(ahora);
        c.setTitulo(titulo.strip());
        c.setFechaInicio(inicio);
        c.setDuracionMinutos(duracionMinutos);
        c.setCupo(cupo);
        c.setCuposOcupados(0);
        c.setInstructorId(instructorId);
        c.setEstado(EstadoCapacitacion.PROGRAMADA);
        return c;
    }

    // ------------------------------------------------------------------
    // HU35 · Actualizar
    // ------------------------------------------------------------------

    public CapacitacionResponse actualizar(ObjectId id, ActualizarCapacitacionRequest solicitud,
            UsuarioAutenticado actor) {
        Capacitacion capacitacion = buscarVisible(id, actor);
        Instant ahora = ahora();
        if (capacitacion.getEstado() == EstadoCapacitacion.CANCELADA) {
            throw new ReglaNegocioException("La capacitación está cancelada y ya no se puede modificar.");
        }
        if (capacitacion.realizada(ahora)) {
            throw new ReglaNegocioException("La capacitación ya se realizó y no se puede modificar.");
        }
        Instant inicio = solicitud.fechaInicio().truncatedTo(ChronoUnit.MILLIS);
        if (!inicio.equals(capacitacion.getFechaInicio())) {
            verificarInicioFuturo(inicio, ahora);
        }
        ObjectId instructor = Ids.deCuerpo(solicitud.instructorId());
        if (!instructor.equals(capacitacion.getInstructorId())) {
            verificarInstructor(instructor);
        }
        if (solicitud.cupo() < capacitacion.getCuposOcupados()) {
            throw cupoMenorQueInscritos(capacitacion.getCuposOcupados());
        }

        Instant leida = capacitacion.getFechaActualizacion();
        capacitacion.setTitulo(solicitud.titulo().strip());
        capacitacion.setFechaInicio(inicio);
        capacitacion.setDuracionMinutos(solicitud.duracionMinutos());
        capacitacion.setCupo(solicitud.cupo());
        capacitacion.setInstructorId(instructor);
        if (solicitud.estado() == EstadoCapacitacion.CANCELADA) {
            capacitacion.setEstado(EstadoCapacitacion.CANCELADA);
        }
        capacitacion.setFechaActualizacion(ahora);
        verificarCoherencia(capacitacion);

        // $set y no un reemplazo: cuposOcupados lo cambian las inscripciones (HU37, HU40) con $inc y no se
        // pisa. La condición de fechaActualizacion detecta otra edición; la de cupo, una inscripción que
        // llegó en medio y dejaría el cupo por debajo de los inscritos.
        Query condicion = new Query(Criteria.where("_id").is(id)
                .and("fechaActualizacion").is(leida)
                .and("cuposOcupados").lte(solicitud.cupo()));
        Update cambios = new Update()
                .set("titulo", capacitacion.getTitulo())
                .set("fechaInicio", capacitacion.getFechaInicio())
                .set("duracionMinutos", capacitacion.getDuracionMinutos())
                .set("fechaFin", capacitacion.getFechaFin())
                .set("cupo", capacitacion.getCupo())
                .set("instructorId", capacitacion.getInstructorId())
                .set("estado", capacitacion.getEstado().name())
                .set("fechaActualizacion", ahora);
        Capacitacion guardada = template.findAndModify(condicion, cambios,
                FindAndModifyOptions.options().returnNew(true), Capacitacion.class);
        if (guardada == null) {
            Capacitacion actual = repositorio.findById(id).orElseThrow(CapacitacionService::noExiste);
            if (!actual.getFechaActualizacion().equals(leida)) {
                throw new ApiException(CodigoError.CONFLICTO, "Otra persona cambió la capacitación mientras usted "
                        + "la editaba. Recárguela e intente de nuevo.");
            }
            throw cupoMenorQueInscritos(actual.getCuposOcupados());
        }
        return detalle(guardada, actor);
    }

    // ------------------------------------------------------------------
    // HU36 · Eliminar
    // ------------------------------------------------------------------

    /**
     * Pasos del modelo 1.2 en una transacción: inscripciones ({@code ux_inscripciones_capacitacion_usuario}),
     * solicitud de vuelta a PENDIENTE y la capacitación. La confirmación la pide el frontend con
     * {@code inscritos} del detalle (HU36).
     */
    public EliminacionCapacitacionResponse eliminar(ObjectId id, UsuarioAutenticado actor) {
        return transacciones.ejecutar(() -> {
            Capacitacion capacitacion = buscarVisible(id, actor);
            long eliminadas = template.remove(new Query(Criteria.where("capacitacionId").is(id)), Inscripcion.class)
                    .getDeletedCount();
            boolean devuelta = false;
            if (capacitacion.getSolicitudId() != null) {
                // Mismos cambios que SolicitudCapacitacion.devolverAPendiente(): conserva atendidaPorId.
                UpdateResult resultado = template.updateFirst(
                        new Query(Criteria.where("_id").is(capacitacion.getSolicitudId())
                                .and("estado").is(EstadoSolicitud.PROGRAMADA.name())
                                .and("capacitacionId").is(id)),
                        new Update().set("estado", EstadoSolicitud.PENDIENTE.name())
                                .set("capacitacionId", null)
                                .set("motivoRechazo", null)
                                .set("fechaActualizacion", ahora()),
                        SolicitudCapacitacion.class);
                devuelta = resultado.getModifiedCount() == 1;
            }
            repositorio.deleteById(id);
            return new EliminacionCapacitacionResponse(eliminadas, devuelta);
        });
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /**
     * La capacitación si el actor puede verla; si no, 404. Un usuario de empresa ve las masivas y las
     * personalizadas de su empresa. Lo usan también las inscripciones.
     */
    Capacitacion buscarVisible(ObjectId id, UsuarioAutenticado actor) {
        Capacitacion capacitacion = repositorio.findById(id).orElseThrow(CapacitacionService::noExiste);
        if (actor.esDeEmpresa() && capacitacion.getEmpresaId() != null
                && !capacitacion.getEmpresaId().equals(actor.empresaId())) {
            throw noExiste();
        }
        return capacitacion;
    }

    /**
     * Para una personalizada (crear o programar): la empresa existe, tiene soporte y tiene contratado
     * el módulo. El orden importa: sin el primer paso, una empresa inexistente saldría como
     * "sin membresía".
     */
    void verificarEmpresaPersonalizada(ObjectId empresaId, ModuloErp modulo) {
        if (!nombresEmpresas.nombres(List.of(empresaId)).containsKey(empresaId)) {
            throw new ReglaNegocioException("La empresa indicada no existe.");
        }
        soporte.exigirSoporteEmpresa(empresaId);
        ContratoEmpresa contrato = membresias.contratoDeEmpresa(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("La membresía de la empresa no tiene un plan vigente."));
        ReglasCapacitacion.verificarModuloContratado(modulo, contrato);
    }

    /** Instructor: activo, de alcance GLOBAL y con {@code ASISTENCIA_MARCAR} en un rol activo. */
    private void verificarInstructor(ObjectId instructorId) {
        boolean valido = permisos.resolver(instructorId)
                .filter(u -> !u.esDeEmpresa() && u.tiene(Permiso.ASISTENCIA_MARCAR))
                .isPresent();
        if (!valido) {
            throw new ReglaNegocioException("El instructor debe ser un usuario activo del personal de soporte "
                    + "con permiso para marcar asistencia.");
        }
    }

    private static void verificarInicioFuturo(Instant inicio, Instant ahora) {
        if (!inicio.isAfter(ahora)) {
            throw new ReglaNegocioException("La fecha y hora de inicio deben ser posteriores a la hora actual.");
        }
    }

    private static ReglaNegocioException cupoMenorQueInscritos(int inscritos) {
        return new ReglaNegocioException("El cupo no puede quedar por debajo de los " + inscritos + " inscritos.");
    }

    /** Red de seguridad del {@code oneOf} por tipo (2.3): 422 antes de que MongoDB responda con un 500. */
    private static void verificarCoherencia(Capacitacion capacitacion) {
        if (!capacitacion.tipoCoherente()) {
            throw new ReglaNegocioException("La empresa y la solicitud no corresponden al tipo de capacitación.");
        }
    }

    /**
     * {@code capacitacionId → id de la inscripción} del actor en esas capacitaciones. Una consulta por
     * página con {@code capacitacionId $in} y {@code usuarioId}, sobre {@code ux_inscripciones_capacitacion_usuario}.
     * Vacío si el actor no se inscribe ({@code INSCRIPCIONES_PROPIAS}).
     */
    Map<ObjectId, ObjectId> misInscripciones(Collection<Capacitacion> capacitaciones, UsuarioAutenticado actor) {
        Map<ObjectId, ObjectId> mias = new HashMap<>();
        if (!actor.tiene(Permiso.INSCRIPCIONES_PROPIAS) || capacitaciones.isEmpty()) {
            return mias;
        }
        List<ObjectId> ids = capacitaciones.stream().map(Capacitacion::getId).toList();
        Query consulta = new Query(Criteria.where("capacitacionId").in(ids).and("usuarioId").is(actor.id()));
        consulta.fields().include("capacitacionId");
        for (Inscripcion i : template.find(consulta, Inscripcion.class)) {
            mias.put(i.getCapacitacionId(), i.getId());
        }
        return mias;
    }

    /** Detalle: lleva {@code inscritos} (conteo real de inscripciones, HU36) y la inscripción del actor. */
    CapacitacionResponse detalle(Capacitacion capacitacion, UsuarioAutenticado actor) {
        long inscritos = template.count(new Query(Criteria.where("capacitacionId").is(capacitacion.getId())),
                Inscripcion.class);
        ObjectId mia = misInscripciones(List.of(capacitacion), actor).get(capacitacion.getId());
        return CapacitacionMapper.aRespuesta(capacitacion, nombres(List.of(capacitacion)), ahora(), inscritos, mia);
    }

    /** Una consulta a usuarios y una a empresas para toda la página. */
    private NombresCapacitacion nombres(Collection<Capacitacion> capacitaciones) {
        return new NombresCapacitacion(nombresUsuarios.nombres(CapacitacionMapper.idsDeUsuarios(capacitaciones)),
                nombresEmpresas.nombres(CapacitacionMapper.idsDeEmpresas(capacitaciones)));
    }

    static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe la capacitación solicitada.");
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
