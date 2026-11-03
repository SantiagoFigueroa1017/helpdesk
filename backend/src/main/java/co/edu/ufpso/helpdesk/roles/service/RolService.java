package co.edu.ufpso.helpdesk.roles.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.roles.dto.ActualizarRolRequest;
import co.edu.ufpso.helpdesk.roles.dto.CrearRolRequest;
import co.edu.ufpso.helpdesk.roles.dto.PermisoResponse;
import co.edu.ufpso.helpdesk.roles.dto.RolAsignableResponse;
import co.edu.ufpso.helpdesk.roles.dto.RolResponse;
import co.edu.ufpso.helpdesk.roles.repository.RolRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.service.UsuariosPorRolService;

/**
 * Roles y permisos dinámicos (HU25 a HU28, contexto 8.4, acta 2.4).
 *
 * <p>Reglas, además de las del validador:
 * <ul>
 *   <li>Anti-escalada: quien gestiona roles solo crea, edita o elimina roles cuyos permisos están
 *       contenidos en sus permisos efectivos. Si su alcance es EMPRESA, solo roles EMPRESA. Al
 *       editar se revisan el rol como está y como quedaría.</li>
 *   <li>Roles del sistema: no cambian nombre ni alcance y no se eliminan.</li>
 *   <li>ADMIN: conserva {@code ROLES_GESTIONAR} y no se desactiva, para que siempre haya quien
 *       gestione roles.</li>
 *   <li>El alcance no cambia si el rol tiene usuarios (HU27). No se elimina un rol con usuarios (HU28).</li>
 *   <li>Todo cambio vacía la entrada del rol en la caché {@code rolesSesion}, así aplica en la
 *       siguiente petición de sus usuarios (HU27).</li>
 * </ul>
 */
@Service
public class RolService {

    /** Nombre del rol del sistema que no puede perder {@code ROLES_GESTIONAR} ni desactivarse. */
    static final String ROL_ADMIN = "ADMIN";

    /** Único campo con índice para ordenar ({@code ux_roles_nombre}). */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre");

    private final RolRepository repositorio;
    private final MongoTemplate template;
    private final UsuariosPorRolService usuariosPorRol;
    private final RolSesionService rolSesion;
    private final Clock reloj;

    public RolService(RolRepository repositorio, MongoTemplate template, UsuariosPorRolService usuariosPorRol,
            RolSesionService rolSesion, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.usuariosPorRol = usuariosPorRol;
        this.rolSesion = rolSesion;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU26 · Consultar
    // ------------------------------------------------------------------

    /** Catálogo completo, en el orden del enum (agrupado por módulo). */
    public List<PermisoResponse> catalogo() {
        return Arrays.stream(Permiso.values()).map(PermisoResponse::de).toList();
    }

    /**
     * Página de roles, con filtros opcionales por alcance y estado. Por defecto ordena por nombre.
     * La colección tiene pocos documentos, así que los filtros no tienen índice propio.
     */
    public Pagina<RolResponse> listar(AlcanceRol alcance, Boolean activo, Pageable pageable) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("nombre"));

        // Dos consultas con el mismo filtro: count respeta skip y limit, así que se cuenta sin paginar.
        long total = template.count(filtro(alcance, activo), Rol.class);
        List<Rol> roles = template.find(filtro(alcance, activo).with(conOrden), Rol.class);

        Map<ObjectId, Long> conteos = usuariosPorRol.contarPorRoles(roles.stream().map(Rol::getId).toList());
        return Pagina.de(new PageImpl<>(roles, conOrden, total), r -> aRespuesta(r, conteos.getOrDefault(r.getId(), 0L)));
    }

    private static Query filtro(AlcanceRol alcance, Boolean activo) {
        Query filtro = new Query();
        if (alcance != null) {
            filtro.addCriteria(Criteria.where("alcance").is(alcance));
        }
        if (activo != null) {
            filtro.addCriteria(Criteria.where("activo").is(activo));
        }
        return filtro;
    }

    public RolResponse obtener(ObjectId id) {
        Rol rol = buscar(id);
        return aRespuesta(rol, usuariosPorRol.contarConRol(id));
    }

    /**
     * HU05 y HU07: roles activos que el actor puede asignar, por nombre. Mismo criterio de la
     * anti-escalada de usuarios: permisos contenidos en los suyos, sin contar los de autoservicio
     * ({@link Permiso#AUTOSERVICIO}), y, si su alcance es EMPRESA, solo roles
     * EMPRESA (el parámetro {@code alcance} se ignora). La colección tiene pocos documentos, así que
     * se filtran en memoria y no se pagina.
     */
    public List<RolAsignableResponse> asignables(AlcanceRol alcance, UsuarioAutenticado actor) {
        AlcanceRol filtro = actor.esDeEmpresa() ? AlcanceRol.EMPRESA : alcance;
        return template.find(filtro(filtro, true).with(Sort.by("nombre")), Rol.class).stream()
                .filter(r -> Permiso.faltantesParaAsignar(Set.copyOf(r.getPermisos()), actor.permisos()).isEmpty())
                .map(r -> new RolAsignableResponse(r.getId().toHexString(), r.getNombre(), r.getDescripcion(),
                        r.getAlcance().name(), enOrden(Set.copyOf(r.getPermisos())).stream().map(Enum::name).toList()))
                .toList();
    }

    // ------------------------------------------------------------------
    // HU25 · Crear
    // ------------------------------------------------------------------

    public RolResponse crear(CrearRolRequest solicitud, UsuarioAutenticado actor) {
        String nombre = solicitud.nombre().strip();
        verificarAntiEscalada(actor, solicitud.permisos(), solicitud.alcance());
        if (repositorio.existsByNombre(nombre)) {
            throw ConflictoException.duplicado("Ya existe un rol con el nombre '" + nombre + "'.");
        }

        Instant ahora = ahora();
        Rol rol = new Rol();
        rol.setFechaCreacion(ahora);
        rol.setFechaActualizacion(ahora);
        rol.setNombre(nombre);
        rol.setDescripcion(solicitud.descripcion().strip());
        rol.setAlcance(solicitud.alcance());
        rol.setPermisos(enOrden(solicitud.permisos()));
        rol.setActivo(true);
        rol.setEsSistema(false);
        return aRespuesta(repositorio.insert(rol), 0);
    }

    // ------------------------------------------------------------------
    // HU27 · Actualizar
    // ------------------------------------------------------------------

    public RolResponse actualizar(ObjectId id, ActualizarRolRequest solicitud, UsuarioAutenticado actor) {
        Rol rol = buscar(id);
        String nombre = solicitud.nombre().strip();

        verificarAntiEscalada(actor, Set.copyOf(rol.getPermisos()), rol.getAlcance());
        verificarAntiEscalada(actor, solicitud.permisos(), solicitud.alcance());

        if (Boolean.TRUE.equals(rol.getEsSistema())
                && (!rol.getNombre().equals(nombre) || rol.getAlcance() != solicitud.alcance())) {
            throw new ReglaNegocioException("Los roles del sistema conservan su nombre y su alcance.");
        }
        if (esAdmin(rol)) {
            if (!solicitud.permisos().contains(Permiso.ROLES_GESTIONAR)) {
                throw new ReglaNegocioException("El rol ADMIN no puede perder el permiso ROLES_GESTIONAR.");
            }
            if (!solicitud.activo()) {
                throw new ReglaNegocioException("El rol ADMIN no se puede desactivar.");
            }
        }

        long usuarios = usuariosPorRol.contarConRol(id);
        if (rol.getAlcance() != solicitud.alcance() && usuarios > 0) {
            throw new ReglaNegocioException("No se puede cambiar el alcance de un rol con usuarios asignados ("
                    + usuarios + ").");
        }
        if (!rol.getNombre().equals(nombre) && repositorio.existsByNombreAndIdNot(nombre, id)) {
            throw ConflictoException.duplicado("Ya existe un rol con el nombre '" + nombre + "'.");
        }

        rol.setNombre(nombre);
        rol.setDescripcion(solicitud.descripcion().strip());
        rol.setAlcance(solicitud.alcance());
        rol.setPermisos(enOrden(solicitud.permisos()));
        rol.setActivo(solicitud.activo());
        rol.setFechaActualizacion(ahora());
        Rol guardado = repositorio.save(rol);
        rolSesion.invalidar(id);
        return aRespuesta(guardado, usuarios);
    }

    // ------------------------------------------------------------------
    // HU28 · Eliminar
    // ------------------------------------------------------------------

    /**
     * Borrado físico. Un rol con usuarios responde 409 con {@code dependientes.usuarios}. Si alguien
     * asigna el rol entre el conteo y el borrado, ese usuario conserva un id de rol inexistente,
     * que {@code PermisosEfectivos} ignora; el CRUD de usuarios (2.5) valida que los roles existan.
     */
    public void eliminar(ObjectId id, UsuarioAutenticado actor) {
        Rol rol = buscar(id);
        verificarAntiEscalada(actor, Set.copyOf(rol.getPermisos()), rol.getAlcance());
        if (Boolean.TRUE.equals(rol.getEsSistema())) {
            throw new ReglaNegocioException("Los roles del sistema no se eliminan.");
        }
        long usuarios = usuariosPorRol.contarConRol(id);
        if (usuarios > 0) {
            throw ConflictoException.tieneDependientes(
                    "No se puede eliminar el rol porque tiene usuarios asignados.", Map.of("usuarios", usuarios));
        }
        repositorio.deleteById(id);
        rolSesion.invalidar(id);
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /**
     * 403 si el rol daría o tiene permisos que el actor no tiene, o si un actor de alcance EMPRESA
     * toca un rol GLOBAL. El detalle nombra los permisos que faltan para que el frontend lo explique.
     */
    private static void verificarAntiEscalada(UsuarioAutenticado actor, Set<Permiso> permisos, AlcanceRol alcance) {
        if (actor.esDeEmpresa() && alcance == AlcanceRol.GLOBAL) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "Un usuario de empresa solo puede gestionar roles de alcance EMPRESA.");
        }
        Set<Permiso> faltantes = EnumSet.noneOf(Permiso.class);
        faltantes.addAll(permisos);
        faltantes.removeAll(actor.permisos());
        if (!faltantes.isEmpty()) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "No puede gestionar un rol con permisos que usted no tiene: "
                            + faltantes.stream().map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
    }

    private Rol buscar(ObjectId id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol solicitado."));
    }

    private static boolean esAdmin(Rol rol) {
        return Boolean.TRUE.equals(rol.getEsSistema()) && ROL_ADMIN.equals(rol.getNombre());
    }

    /** Orden del catálogo, para que el documento guardado no dependa del orden de la petición. */
    private static List<Permiso> enOrden(Set<Permiso> permisos) {
        return List.copyOf(EnumSet.copyOf(permisos));
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static RolResponse aRespuesta(Rol r, long usuarios) {
        return new RolResponse(
                r.getId().toHexString(),
                r.getNombre(),
                r.getDescripcion(),
                r.getAlcance().name(),
                enOrden(Set.copyOf(r.getPermisos())).stream().map(Enum::name).toList(),
                Boolean.TRUE.equals(r.getActivo()),
                Boolean.TRUE.equals(r.getEsSistema()),
                usuarios,
                r.getFechaCreacion(),
                r.getFechaActualizacion());
    }
}
