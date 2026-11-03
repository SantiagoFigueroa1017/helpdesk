package co.edu.ufpso.helpdesk.empresas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.capacitaciones.service.CapacitacionesPorEmpresaService;
import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.empresas.domain.Contacto;
import co.edu.ufpso.helpdesk.empresas.domain.Empresa;
import co.edu.ufpso.helpdesk.empresas.dto.ActualizarEmpresaRequest;
import co.edu.ufpso.helpdesk.empresas.dto.ContactoRequest;
import co.edu.ufpso.helpdesk.empresas.dto.CrearEmpresaRequest;
import co.edu.ufpso.helpdesk.empresas.dto.EmpresaResponse;
import co.edu.ufpso.helpdesk.empresas.repository.EmpresaRepository;
import co.edu.ufpso.helpdesk.membresias.service.MembresiasPorEmpresaService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.service.TicketsPorEmpresaService;
import co.edu.ufpso.helpdesk.usuarios.service.UsuariosPorEmpresaService;

/**
 * Empresas clientes (HU01 a HU04, modelo 1.2 sección 1, matriz de permisos 2.4).
 *
 * <ul>
 *   <li>NIT único: 409 {@code DUPLICADO}.</li>
 *   <li>Alcance EMPRESA: solo ve y edita el contacto de su propia empresa (el {@code empresaId} de
 *       {@link UsuarioAutenticado}); otra responde 404 (protección IDOR).</li>
 *   <li>Borrado bloqueado si hay usuarios, membresía, pagos, tickets, capacitaciones personalizadas
 *       o solicitudes: 409 {@code TIENE_DEPENDIENTES} con el conteo de cada uno (HU04 v.4).</li>
 * </ul>
 */
@Service
public class EmpresaService {

    /** Campos con índice para ordenar: {@code ix_empresas_razonSocial} y {@code ux_empresas_nit}. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("razonSocial", "nit");

    private final EmpresaRepository repositorio;
    private final MongoTemplate template;
    private final UsuariosPorEmpresaService usuarios;
    private final MembresiasPorEmpresaService membresias;
    private final TicketsPorEmpresaService tickets;
    private final CapacitacionesPorEmpresaService capacitaciones;
    private final Clock reloj;

    public EmpresaService(EmpresaRepository repositorio, MongoTemplate template, UsuariosPorEmpresaService usuarios,
            MembresiasPorEmpresaService membresias, TicketsPorEmpresaService tickets,
            CapacitacionesPorEmpresaService capacitaciones, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.tickets = tickets;
        this.capacitaciones = capacitaciones;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU02 · Consultar
    // ------------------------------------------------------------------

    /**
     * Página de empresas. {@code texto} busca por el comienzo de la razón social sin distinguir
     * mayúsculas: MongoDB recorre el índice {@code ix_empresas_razonSocial} y solo lee los documentos
     * que coinciden. {@code nit} busca el NIT exacto con {@code ux_empresas_nit}. Para alcance
     * EMPRESA el resultado es solo su empresa, lleguen los filtros que lleguen.
     */
    public Pagina<EmpresaResponse> listar(String texto, String nit, Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("razonSocial"));

        long total = template.count(filtro(texto, nit, actor), Empresa.class);
        List<Empresa> empresas = template.find(filtro(texto, nit, actor).with(conOrden), Empresa.class);
        return Pagina.de(new PageImpl<>(empresas, conOrden, total), EmpresaService::aRespuesta);
    }

    private static Query filtro(String texto, String nit, UsuarioAutenticado actor) {
        Query filtro = new Query();
        if (actor.esDeEmpresa()) {
            filtro.addCriteria(Criteria.where("_id").is(actor.empresaId()));
        }
        if (texto != null && !texto.isBlank()) {
            filtro.addCriteria(Criteria.where("razonSocial").regex("^" + Pattern.quote(texto.strip()), "i"));
        }
        if (nit != null && !nit.isBlank()) {
            filtro.addCriteria(Criteria.where("nit").is(nit.strip()));
        }
        return filtro;
    }

    public EmpresaResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        return aRespuesta(buscarVisible(id, actor));
    }

    /** Para otros módulos (membresías, usuarios): si la empresa existe. Usa el {@code _id}. */
    public boolean existe(ObjectId id) {
        return id != null && repositorio.existsById(id);
    }

    // ------------------------------------------------------------------
    // HU01 · Crear
    // ------------------------------------------------------------------

    public EmpresaResponse crear(CrearEmpresaRequest solicitud) {
        String nit = solicitud.nit().strip();
        if (repositorio.existsByNit(nit)) {
            throw ConflictoException.duplicado("Ya existe una empresa con el NIT " + nit + ".");
        }

        Instant ahora = ahora();
        Empresa empresa = new Empresa();
        empresa.setFechaCreacion(ahora);
        empresa.setFechaActualizacion(ahora);
        empresa.setNit(nit);
        empresa.setRazonSocial(solicitud.razonSocial().strip());
        empresa.setContacto(aContacto(solicitud.contacto()));
        return aRespuesta(repositorio.insert(empresa));
    }

    // ------------------------------------------------------------------
    // HU03 · Actualizar
    // ------------------------------------------------------------------

    /** Edición completa (permiso {@code EMPRESAS_EDITAR}, solo roles GLOBAL). */
    public EmpresaResponse actualizar(ObjectId id, ActualizarEmpresaRequest solicitud) {
        Empresa empresa = buscar(id);
        String nit = solicitud.nit().strip();
        if (!empresa.getNit().equals(nit) && repositorio.existsByNitAndIdNot(nit, id)) {
            throw ConflictoException.duplicado("Ya existe una empresa con el NIT " + nit + ".");
        }

        empresa.setNit(nit);
        empresa.setRazonSocial(solicitud.razonSocial().strip());
        empresa.setContacto(aContacto(solicitud.contacto()));
        empresa.setFechaActualizacion(ahora());
        return aRespuesta(repositorio.save(empresa));
    }

    /**
     * Solo el contacto. El CLIENTE_ADMIN ({@code EMPRESAS_EDITAR_CONTACTO}) únicamente sobre su
     * empresa; cualquier otra responde 404.
     */
    public EmpresaResponse actualizarContacto(ObjectId id, ContactoRequest solicitud, UsuarioAutenticado actor) {
        Empresa empresa = buscarVisible(id, actor);
        empresa.setContacto(aContacto(solicitud));
        empresa.setFechaActualizacion(ahora());
        return aRespuesta(repositorio.save(empresa));
    }

    // ------------------------------------------------------------------
    // HU04 · Eliminar
    // ------------------------------------------------------------------

    /**
     * Borrado físico si nada depende de la empresa. Cada conteo usa el índice de su colección que
     * empieza por {@code empresaId}. El detalle solo trae las colecciones con dependientes.
     */
    public void eliminar(ObjectId id) {
        buscar(id);
        Map<String, Long> dependientes = new LinkedHashMap<>();
        agregar(dependientes, "usuarios", usuarios.contar(id));
        agregar(dependientes, "membresias", membresias.contarMembresias(id));
        agregar(dependientes, "pagos", membresias.contarPagos(id));
        agregar(dependientes, "tickets", tickets.contar(id));
        agregar(dependientes, "capacitaciones", capacitaciones.contarPersonalizadas(id));
        agregar(dependientes, "solicitudesCapacitacion", capacitaciones.contarSolicitudes(id));
        if (!dependientes.isEmpty()) {
            throw ConflictoException.tieneDependientes(
                    "No se puede eliminar la empresa porque tiene registros asociados.", dependientes);
        }
        repositorio.deleteById(id);
    }

    private static void agregar(Map<String, Long> dependientes, String coleccion, long cantidad) {
        if (cantidad > 0) {
            dependientes.put(coleccion, cantidad);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Empresa buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(EmpresaService::noExiste);
    }

    /** Para alcance EMPRESA, una empresa distinta de la suya responde 404 sin consultar. */
    private Empresa buscarVisible(ObjectId id, UsuarioAutenticado actor) {
        if (actor.esDeEmpresa() && !id.equals(actor.empresaId())) {
            throw noExiste();
        }
        return buscar(id);
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe la empresa solicitada.");
    }

    /** El correo se guarda sin espacios y en minúsculas; dirección y ciudad vacías no se guardan. */
    private static Contacto aContacto(ContactoRequest c) {
        return new Contacto(c.nombre().strip(), c.email().strip().toLowerCase(Locale.ROOT), c.telefono().strip(),
                opcional(c.direccion()), opcional(c.ciudad()));
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static EmpresaResponse aRespuesta(Empresa e) {
        Contacto c = e.getContacto();
        return new EmpresaResponse(
                e.getId().toHexString(),
                e.getNit(),
                e.getRazonSocial(),
                new EmpresaResponse.Contacto(c.nombre(), c.email(), c.telefono(), c.direccion(), c.ciudad()),
                e.getFechaCreacion(),
                e.getFechaActualizacion());
    }
}
