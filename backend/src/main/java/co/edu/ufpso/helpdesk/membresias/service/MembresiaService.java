package co.edu.ufpso.helpdesk.membresias.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.empresas.service.EmpresaService;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.membresias.domain.MotivoSinSoporte;
import co.edu.ufpso.helpdesk.membresias.dto.ActualizarMembresiaRequest;
import co.edu.ufpso.helpdesk.membresias.dto.CrearMembresiaRequest;
import co.edu.ufpso.helpdesk.membresias.dto.MembresiaResponse;
import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;
import co.edu.ufpso.helpdesk.membresias.repository.PagoRepository;
import co.edu.ufpso.helpdesk.planes.dto.PlanResponse;
import co.edu.ufpso.helpdesk.planes.service.PlanService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Membresías (HU13 a HU16, modelo 1.2 sección 5, contexto 7.5).
 *
 * <ul>
 *   <li>Una por empresa: 409 {@code DUPLICADO}. La empresa debe existir y no cambia en HU15.</li>
 *   <li>Nace PENDIENTE. Pasa a ACTIVA o SUSPENDIDA con los pagos ({@link PagoService}) o a mano en HU15.</li>
 *   <li>Plan: debe existir; al asignarlo o cambiarlo debe estar activo (HU13, HU11).</li>
 *   <li>{@code maxModulos} del plan: se revisa al crear y cuando HU15 agrega módulos. Bajar el
 *       límite del plan o cambiar a un plan más pequeño sin agregar módulos no obliga a quitar nada.</li>
 *   <li>{@code fechaVencimiento} posterior a {@code fechaInicio}: 422.</li>
 *   <li>Alcance EMPRESA: solo la membresía de su empresa; otra responde 404.</li>
 *   <li>Con pagos no se elimina: 409 {@code TIENE_DEPENDIENTES} (HU16).</li>
 * </ul>
 */
@Service
public class MembresiaService {

    /** Sin campo de orden expuesto: el listado sale por {@code _id} (orden de creación). */
    private static final Set<String> ORDEN_PERMITIDO = Set.of();

    private final MembresiaRepository repositorio;
    private final PagoRepository pagos;
    private final MongoTemplate template;
    private final EmpresaService empresas;
    private final PlanService planes;
    private final Clock reloj;

    public MembresiaService(MembresiaRepository repositorio, PagoRepository pagos, MongoTemplate template,
            EmpresaService empresas, PlanService planes, Clock reloj) {
        this.repositorio = repositorio;
        this.pagos = pagos;
        this.template = template;
        this.empresas = empresas;
        this.planes = planes;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU14 · Consultar
    // ------------------------------------------------------------------

    /** Filtros opcionales para alcance GLOBAL; un usuario de empresa solo recibe la suya. */
    public Pagina<MembresiaResponse> listar(String empresaId, String planId, EstadoMembresia estado,
            Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));

        Query filtro = new Query();
        ObjectId empresa = actor.esDeEmpresa() ? actor.empresaId() : Ids.deParametro("empresaId", empresaId);
        if (empresa != null) {
            filtro.addCriteria(Criteria.where("empresaId").is(empresa));
        }
        ObjectId plan = Ids.deParametro("planId", planId);
        if (plan != null) {
            filtro.addCriteria(Criteria.where("planId").is(plan));
        }
        if (estado != null) {
            filtro.addCriteria(Criteria.where("estado").is(estado));
        }

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(filtro, Membresia.class);
        List<Membresia> membresias = template.find(filtro.with(conOrden), Membresia.class);
        Instant ahora = ahora();
        return Pagina.de(new PageImpl<>(membresias, conOrden, total), m -> aRespuesta(m, ahora));
    }

    public MembresiaResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        Membresia membresia = buscar(id);
        if (actor.esDeEmpresa() && !membresia.getEmpresaId().equals(actor.empresaId())) {
            throw noExiste();
        }
        return aRespuesta(membresia, ahora());
    }

    /**
     * Para el módulo de usuarios (HU05, HU07): el plan de la membresía de la empresa, con sus
     * límites. Vacío si la empresa no tiene membresía (o su plan ya no existe).
     */
    public Optional<PlanResponse> planDeEmpresa(ObjectId empresaId) {
        return repositorio.findByEmpresaId(empresaId).flatMap(m -> planes.consultar(m.getPlanId()));
    }

    /**
     * Para tickets (HU21) y capacitaciones (2.8): módulos contratados, plan y vigencia de la
     * empresa. Vacío si la empresa no tiene membresía o su plan ya no existe. No revisa el soporte:
     * eso lo hace {@link SoporteService} antes de llamar aquí.
     */
    public Optional<ContratoEmpresa> contratoDeEmpresa(ObjectId empresaId) {
        return repositorio.findByEmpresaId(empresaId).flatMap(m -> planes.consultar(m.getPlanId())
                .map(plan -> new ContratoEmpresa(EnumSet.copyOf(m.getModulosContratados()), plan,
                        m.getFechaInicio(), m.getFechaVencimiento())));
    }

    // ------------------------------------------------------------------
    // HU13 · Crear
    // ------------------------------------------------------------------

    public MembresiaResponse crear(CrearMembresiaRequest solicitud) {
        ObjectId empresaId = Ids.deCuerpo(solicitud.empresaId());
        if (!empresas.existe(empresaId)) {
            throw new ReglaNegocioException("La empresa indicada no existe.");
        }
        if (repositorio.existsByEmpresaId(empresaId)) {
            throw ConflictoException.duplicado("La empresa ya tiene una membresía. Edítela en lugar de crear otra.");
        }
        PlanResponse plan = planActivo(Ids.deCuerpo(solicitud.planId()));
        verificarModulos(solicitud.modulosContratados(), plan, 0);
        Instant inicio = enMilisegundos(solicitud.fechaInicio());
        Instant vencimiento = enMilisegundos(solicitud.fechaVencimiento());
        verificarFechas(inicio, vencimiento);

        Instant ahora = ahora();
        Membresia membresia = new Membresia();
        membresia.setFechaCreacion(ahora);
        membresia.setFechaActualizacion(ahora);
        membresia.setEmpresaId(empresaId);
        membresia.setPlanId(new ObjectId(plan.id()));
        membresia.setModulosContratados(enOrden(solicitud.modulosContratados()));
        membresia.setFechaInicio(inicio);
        membresia.setFechaVencimiento(vencimiento);
        membresia.setEstado(EstadoMembresia.PENDIENTE);
        return aRespuesta(repositorio.insert(membresia), ahora);
    }

    // ------------------------------------------------------------------
    // HU15 · Actualizar
    // ------------------------------------------------------------------

    public MembresiaResponse actualizar(ObjectId id, ActualizarMembresiaRequest solicitud) {
        Membresia membresia = buscar(id);
        ObjectId planId = Ids.deCuerpo(solicitud.planId());
        PlanResponse plan = planId.equals(membresia.getPlanId())
                ? planes.consultar(planId).orElseThrow(() -> new ReglaNegocioException("El plan de la membresía ya no existe."))
                : planActivo(planId);
        verificarModulos(solicitud.modulosContratados(), plan, membresia.getModulosContratados().size());
        Instant inicio = enMilisegundos(solicitud.fechaInicio());
        Instant vencimiento = enMilisegundos(solicitud.fechaVencimiento());
        verificarFechas(inicio, vencimiento);

        Instant ahora = ahora();
        membresia.setPlanId(planId);
        membresia.setModulosContratados(enOrden(solicitud.modulosContratados()));
        membresia.setFechaInicio(inicio);
        membresia.setFechaVencimiento(vencimiento);
        membresia.setEstado(solicitud.estado());
        membresia.setFechaActualizacion(ahora);
        return aRespuesta(repositorio.save(membresia), ahora);
    }

    // ------------------------------------------------------------------
    // HU16 · Eliminar
    // ------------------------------------------------------------------

    public void eliminar(ObjectId id) {
        buscar(id);
        long conteo = pagos.countByMembresiaId(id);
        if (conteo > 0) {
            throw ConflictoException.tieneDependientes(
                    "No se puede eliminar la membresía porque tiene pagos registrados.", Map.of("pagos", conteo));
        }
        repositorio.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    private PlanResponse planActivo(ObjectId planId) {
        PlanResponse plan = planes.consultar(planId)
                .orElseThrow(() -> new ReglaNegocioException("El plan indicado no existe."));
        if (!plan.activo()) {
            throw new ReglaNegocioException("El plan '" + plan.nombre() + "' está inactivo y no se puede asignar.");
        }
        return plan;
    }

    /** Solo bloquea si la acción deja más módulos que el plan y además agrega módulos. */
    private static void verificarModulos(Set<ModuloErp> modulos, PlanResponse plan, int modulosActuales) {
        int maximo = plan.limites().maxModulos();
        if (modulos.size() > maximo && modulos.size() > modulosActuales) {
            throw new ReglaNegocioException("El plan '" + plan.nombre() + "' permite como máximo " + maximo
                    + " módulo(s) y se pidieron " + modulos.size() + ".");
        }
    }

    private static void verificarFechas(Instant inicio, Instant vencimiento) {
        if (!vencimiento.isAfter(inicio)) {
            throw new ReglaNegocioException("La fecha de vencimiento debe ser posterior a la fecha de inicio.");
        }
    }

    private Membresia buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(MembresiaService::noExiste);
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe la membresía solicitada.");
    }

    private static List<ModuloErp> enOrden(Set<ModuloErp> modulos) {
        return List.copyOf(EnumSet.copyOf(modulos));
    }

    private static Instant enMilisegundos(Instant fecha) {
        return fecha.truncatedTo(ChronoUnit.MILLIS);
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    static MembresiaResponse aRespuesta(Membresia m, Instant ahora) {
        MotivoSinSoporte motivo = m.motivoSinSoporte(ahora);
        String estadoVisible = motivo == MotivoSinSoporte.VENCIDA ? "VENCIDA" : m.getEstado().name();
        return new MembresiaResponse(
                m.getId().toHexString(),
                m.getEmpresaId().toHexString(),
                m.getPlanId().toHexString(),
                m.getModulosContratados().stream().map(Enum::name).toList(),
                m.getFechaInicio(),
                m.getFechaVencimiento(),
                m.getEstado().name(),
                estadoVisible,
                new EstadoSoporte(motivo == null, motivo == null ? null : motivo.name()),
                m.getFechaCreacion(),
                m.getFechaActualizacion());
    }
}
