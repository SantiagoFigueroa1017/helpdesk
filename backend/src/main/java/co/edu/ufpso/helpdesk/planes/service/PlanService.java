package co.edu.ufpso.helpdesk.planes.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.membresias.service.MembresiasPorPlanService;
import co.edu.ufpso.helpdesk.planes.domain.LimitesPlan;
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.domain.Plan;
import co.edu.ufpso.helpdesk.planes.dto.ActualizarPlanRequest;
import co.edu.ufpso.helpdesk.planes.dto.CrearPlanRequest;
import co.edu.ufpso.helpdesk.planes.dto.LimitesPlanRequest;
import co.edu.ufpso.helpdesk.planes.dto.PlanResponse;
import co.edu.ufpso.helpdesk.planes.repository.PlanRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Planes (HU09 a HU12, modelo 1.2 sección 4, matriz de permisos 2.4).
 *
 * <ul>
 *   <li>Nombre único: 409 {@code DUPLICADO}.</li>
 *   <li>Un usuario de alcance EMPRESA solo ve planes activos; uno inactivo le responde 404 (HU10).</li>
 *   <li>Editar no toca membresías ni pagos: el precio pagado queda en cada pago y los límites
 *       nuevos se aplican a las acciones siguientes (HU11 v.4).</li>
 *   <li>Un plan con membresías no se elimina: 409 {@code TIENE_DEPENDIENTES} con el conteo (HU12).</li>
 * </ul>
 */
@Service
public class PlanService {

    /** Único campo con índice para ordenar ({@code ux_planes_nombre}). */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre");

    private final PlanRepository repositorio;
    private final MongoTemplate template;
    private final MembresiasPorPlanService membresiasPorPlan;
    private final Clock reloj;

    public PlanService(PlanRepository repositorio, MongoTemplate template, MembresiasPorPlanService membresiasPorPlan,
            Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.membresiasPorPlan = membresiasPorPlan;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU10 · Consultar
    // ------------------------------------------------------------------

    /**
     * Página de planes con filtros opcionales. Para alcance EMPRESA el filtro {@code activo} se
     * fuerza a {@code true}, llegue lo que llegue. La colección tiene pocos documentos, así que los
     * filtros no tienen índice propio; el orden por nombre usa {@code ux_planes_nombre}.
     */
    public Pagina<PlanResponse> listar(Boolean activo, NivelAcceso nivelAcceso, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("nombre"));
        Boolean soloActivos = actor.esDeEmpresa() ? Boolean.TRUE : activo;

        long total = template.count(filtro(soloActivos, nivelAcceso), Plan.class);
        List<Plan> planes = template.find(filtro(soloActivos, nivelAcceso).with(conOrden), Plan.class);
        return Pagina.de(new PageImpl<>(planes, conOrden, total), PlanService::aRespuesta);
    }

    private static Query filtro(Boolean activo, NivelAcceso nivelAcceso) {
        Query filtro = new Query();
        if (activo != null) {
            filtro.addCriteria(Criteria.where("activo").is(activo));
        }
        if (nivelAcceso != null) {
            filtro.addCriteria(Criteria.where("nivelAcceso").is(nivelAcceso));
        }
        return filtro;
    }

    public PlanResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        Plan plan = buscar(id);
        if (actor.esDeEmpresa() && !Boolean.TRUE.equals(plan.getActivo())) {
            throw noExiste();
        }
        return aRespuesta(plan);
    }

    // ------------------------------------------------------------------
    // HU09 · Crear
    // ------------------------------------------------------------------

    public PlanResponse crear(CrearPlanRequest solicitud) {
        String nombre = solicitud.nombre().strip();
        if (repositorio.existsByNombre(nombre)) {
            throw ConflictoException.duplicado("Ya existe un plan con el nombre '" + nombre + "'.");
        }

        Instant ahora = ahora();
        Plan plan = new Plan();
        plan.setFechaCreacion(ahora);
        plan.setFechaActualizacion(ahora);
        plan.setNombre(nombre);
        plan.setDescripcion(solicitud.descripcion().strip());
        plan.setPrecio(solicitud.precio());
        plan.setNivelAcceso(solicitud.nivelAcceso());
        plan.setLimites(aLimites(solicitud.limites()));
        plan.setActivo(true);
        return aRespuesta(repositorio.insert(plan));
    }

    // ------------------------------------------------------------------
    // HU11 · Actualizar
    // ------------------------------------------------------------------

    public PlanResponse actualizar(ObjectId id, ActualizarPlanRequest solicitud) {
        Plan plan = buscar(id);
        String nombre = solicitud.nombre().strip();
        if (!plan.getNombre().equals(nombre) && repositorio.existsByNombreAndIdNot(nombre, id)) {
            throw ConflictoException.duplicado("Ya existe un plan con el nombre '" + nombre + "'.");
        }

        plan.setNombre(nombre);
        plan.setDescripcion(solicitud.descripcion().strip());
        plan.setPrecio(solicitud.precio());
        plan.setNivelAcceso(solicitud.nivelAcceso());
        plan.setLimites(aLimites(solicitud.limites()));
        plan.setActivo(solicitud.activo());
        plan.setFechaActualizacion(ahora());
        return aRespuesta(repositorio.save(plan));
    }

    // ------------------------------------------------------------------
    // HU12 · Eliminar
    // ------------------------------------------------------------------

    /**
     * Borrado físico. Si una membresía se asigna al plan entre el conteo y el borrado, queda con un
     * {@code planId} inexistente: es el riesgo aceptado del modelo (reglas de eliminación).
     */
    public void eliminar(ObjectId id) {
        buscar(id);
        long membresias = membresiasPorPlan.contarConPlan(id);
        if (membresias > 0) {
            throw ConflictoException.tieneDependientes(
                    "No se puede eliminar el plan porque tiene membresías asignadas.",
                    Map.of("membresias", membresias));
        }
        repositorio.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Plan buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(PlanService::noExiste);
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe el plan solicitado.");
    }

    private static LimitesPlan aLimites(LimitesPlanRequest l) {
        return new LimitesPlan(l.horasRespuesta(), l.capacitacionesIncluidas(), l.maxUsuarios(), l.maxModulos());
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static PlanResponse aRespuesta(Plan p) {
        LimitesPlan l = p.getLimites();
        return new PlanResponse(
                p.getId().toHexString(),
                p.getNombre(),
                p.getDescripcion(),
                p.getPrecio(),
                p.getNivelAcceso().name(),
                new PlanResponse.Limites(l.horasRespuesta(), l.capacitacionesIncluidas(), l.maxUsuarios(),
                        l.maxModulos()),
                Boolean.TRUE.equals(p.getActivo()),
                p.getFechaCreacion(),
                p.getFechaActualizacion());
    }
}
