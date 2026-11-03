package co.edu.ufpso.helpdesk.usuarios.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.ufpso.helpdesk.auth.service.SesionesUsuarioService;
import co.edu.ufpso.helpdesk.capacitaciones.service.CapacitacionesPorUsuarioService;
import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ConflictoException;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.empresas.service.EmpresaService;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.planes.dto.PlanResponse;
import co.edu.ufpso.helpdesk.roles.service.RolSesion;
import co.edu.ufpso.helpdesk.roles.service.RolSesionService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.service.TicketsPorUsuarioService;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;
import co.edu.ufpso.helpdesk.usuarios.dto.ActualizarPerfilRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.ActualizarUsuarioRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.CrearUsuarioRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.UsuarioResponse;
import co.edu.ufpso.helpdesk.usuarios.repository.UsuarioRepository;

/**
 * Usuarios (HU05 a HU08) y perfil propio (contexto 8.4, matriz de permisos 2.4, decisiones de 2.5).
 *
 * <ul>
 *   <li>Alcance EMPRESA: crea usuarios solo en su empresa y ve, edita y elimina solo los de su
 *       empresa; otro responde 404.</li>
 *   <li>Roles: existen, están activos y comparten alcance (422). Alcance EMPRESA exige empresa;
 *       GLOBAL la prohíbe. La empresa de un usuario no cambia en HU07.</li>
 *   <li>Anti-escalada (403): el actor solo asigna roles con permisos contenidos en los suyos (sin
 *       contar los de autoservicio, {@link Permiso#AUTOSERVICIO}) y, si
 *       su alcance es EMPRESA, solo roles EMPRESA. Tampoco edita ni elimina a un usuario que tenga
 *       permisos que él no tiene.</li>
 *   <li>{@code maxUsuarios} del plan: crear o reactivar un usuario de empresa exige que la empresa
 *       tenga membresía y que no se supere el límite de usuarios activos (422).</li>
 *   <li>Nadie se desactiva ni se elimina a sí mismo (422).</li>
 *   <li>Desactivar o cambiar la contraseña cierra las sesiones del usuario. Todo cambio vacía su
 *       entrada de la caché {@code usuariosSesion}, así aplica en la siguiente petición.</li>
 *   <li>HU08: bloquean el borrado sus tickets (creados, como solicitante o asignados) y las
 *       capacitaciones futuras donde es instructor (409). Si nada lo bloquea, una transacción cierra
 *       sus sesiones, libera sus cupos, borra sus inscripciones y borra el usuario.</li>
 * </ul>
 */
@Service
public class UsuarioService {

    /** Con {@code empresaId}, el orden por nombre usa {@code ix_usuarios_empresa_nombre}. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("nombre");

    private final UsuarioRepository repositorio;
    private final MongoTemplate template;
    private final PasswordEncoder passwordEncoder;
    private final RolSesionService roles;
    private final EmpresaService empresas;
    private final MembresiaService membresias;
    private final UsuarioSesionService usuarioSesion;
    private final SesionesUsuarioService sesiones;
    private final TicketsPorUsuarioService tickets;
    private final CapacitacionesPorUsuarioService capacitaciones;
    private final Clock reloj;

    public UsuarioService(UsuarioRepository repositorio, MongoTemplate template, PasswordEncoder passwordEncoder,
            RolSesionService roles, EmpresaService empresas, MembresiaService membresias,
            UsuarioSesionService usuarioSesion, SesionesUsuarioService sesiones, TicketsPorUsuarioService tickets,
            CapacitacionesPorUsuarioService capacitaciones, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
        this.empresas = empresas;
        this.membresias = membresias;
        this.usuarioSesion = usuarioSesion;
        this.sesiones = sesiones;
        this.tickets = tickets;
        this.capacitaciones = capacitaciones;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU06 · Consultar
    // ------------------------------------------------------------------

    /**
     * {@code texto} busca por el comienzo del nombre sin distinguir mayúsculas. Un usuario de
     * empresa solo recibe los de la suya; el filtro {@code empresaId} solo aplica a GLOBAL.
     */
    public Pagina<UsuarioResponse> listar(String empresaId, Boolean activo, String texto, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("nombre"));

        Query filtro = new Query();
        ObjectId empresa = actor.esDeEmpresa() ? actor.empresaId() : Ids.deParametro("empresaId", empresaId);
        if (empresa != null) {
            filtro.addCriteria(Criteria.where("empresaId").is(empresa));
        }
        if (activo != null) {
            filtro.addCriteria(Criteria.where("activo").is(activo));
        }
        if (texto != null && !texto.isBlank()) {
            filtro.addCriteria(Criteria.where("nombre").regex("^" + Pattern.quote(texto.strip()), "i"));
        }

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(filtro, Usuario.class);
        List<Usuario> usuarios = template.find(filtro.with(conOrden), Usuario.class);
        return Pagina.de(new PageImpl<>(usuarios, conOrden, total), this::aRespuesta);
    }

    public UsuarioResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        return aRespuesta(buscarVisible(id, actor));
    }

    // ------------------------------------------------------------------
    // HU05 · Crear
    // ------------------------------------------------------------------

    public UsuarioResponse crear(CrearUsuarioRequest solicitud, UsuarioAutenticado actor) {
        List<RolSesion> rolesNuevos = resolverRoles(solicitud.rolIds());
        verificarAntiEscalada(actor, rolesNuevos);
        AlcanceRol alcance = rolesNuevos.get(0).alcance();

        ObjectId empresaId = null;
        if (alcance == AlcanceRol.EMPRESA) {
            empresaId = actor.esDeEmpresa() ? actor.empresaId() : idOpcional(solicitud.empresaId());
            if (empresaId == null) {
                throw new ReglaNegocioException("Un usuario con roles de alcance EMPRESA debe pertenecer a una empresa.");
            }
            if (!empresas.existe(empresaId)) {
                throw new ReglaNegocioException("La empresa indicada no existe.");
            }
            verificarCupoDeUsuarios(empresaId);
        } else if (idOpcional(solicitud.empresaId()) != null) {
            throw new ReglaNegocioException("Un usuario con roles de alcance GLOBAL no pertenece a ninguna empresa.");
        }

        Instant ahora = ahora();
        Usuario usuario = new Usuario();
        usuario.setFechaCreacion(ahora);
        usuario.setFechaActualizacion(ahora);
        usuario.setNombre(solicitud.nombre().strip());
        usuario.setEmail(solicitud.email());
        if (repositorio.existsByEmail(usuario.getEmail())) {
            throw ConflictoException.duplicado("Ya existe un usuario con el correo " + usuario.getEmail() + ".");
        }
        usuario.setPasswordHash(passwordEncoder.encode(solicitud.password()));
        usuario.setRolIds(rolesNuevos.stream().map(RolSesion::id).toList());
        usuario.setEmpresaId(empresaId);
        usuario.setActivo(true);
        return aRespuesta(repositorio.insert(usuario));
    }

    // ------------------------------------------------------------------
    // HU07 · Actualizar
    // ------------------------------------------------------------------

    public UsuarioResponse actualizar(ObjectId id, ActualizarUsuarioRequest solicitud, UsuarioAutenticado actor) {
        Usuario usuario = buscarVisible(id, actor);
        verificarAntiEscalada(actor, rolesExistentes(usuario.getRolIds()));
        List<RolSesion> rolesNuevos = resolverRoles(solicitud.rolIds());
        verificarAntiEscalada(actor, rolesNuevos);

        AlcanceRol alcanceActual = usuario.getEmpresaId() == null ? AlcanceRol.GLOBAL : AlcanceRol.EMPRESA;
        if (rolesNuevos.get(0).alcance() != alcanceActual) {
            throw new ReglaNegocioException("Los roles deben ser de alcance " + alcanceActual
                    + ": un usuario no cambia de alcance ni de empresa.");
        }
        boolean estabaActivo = Boolean.TRUE.equals(usuario.getActivo());
        boolean quedaActivo = solicitud.activo();
        if (!quedaActivo && id.equals(actor.id())) {
            throw new ReglaNegocioException("No puede desactivar su propio usuario.");
        }
        if (quedaActivo && !estabaActivo && usuario.getEmpresaId() != null) {
            verificarCupoDeUsuarios(usuario.getEmpresaId());
        }

        String emailAnterior = usuario.getEmail();
        usuario.setEmail(solicitud.email());
        if (!usuario.getEmail().equals(emailAnterior) && repositorio.existsByEmailAndIdNot(usuario.getEmail(), id)) {
            throw ConflictoException.duplicado("Ya existe un usuario con el correo " + usuario.getEmail() + ".");
        }
        usuario.setNombre(solicitud.nombre().strip());
        usuario.setRolIds(rolesNuevos.stream().map(RolSesion::id).toList());
        usuario.setActivo(quedaActivo);
        boolean cambiaPassword = solicitud.nuevaPassword() != null;
        if (cambiaPassword) {
            usuario.setPasswordHash(passwordEncoder.encode(solicitud.nuevaPassword()));
        }
        usuario.setFechaActualizacion(ahora());
        Usuario guardado = repositorio.save(usuario);

        if ((estabaActivo && !quedaActivo) || cambiaPassword) {
            sesiones.cerrarTodas(id);
        }
        usuarioSesion.invalidarSesion(id);
        return aRespuesta(guardado);
    }

    // ------------------------------------------------------------------
    // HU08 · Eliminar
    // ------------------------------------------------------------------

    @Transactional
    public void eliminar(ObjectId id, UsuarioAutenticado actor) {
        Usuario usuario = buscarVisible(id, actor);
        if (id.equals(actor.id())) {
            throw new ReglaNegocioException("No puede eliminar su propio usuario.");
        }
        verificarAntiEscalada(actor, rolesExistentes(usuario.getRolIds()));

        Instant ahora = ahora();
        Map<String, Long> dependientes = new LinkedHashMap<>();
        long conTickets = tickets.contar(id);
        if (conTickets > 0) {
            dependientes.put("tickets", conTickets);
        }
        long comoInstructor = capacitaciones.contarFuturasComoInstructor(id, ahora);
        if (comoInstructor > 0) {
            dependientes.put("capacitaciones", comoInstructor);
        }
        if (!dependientes.isEmpty()) {
            throw ConflictoException.tieneDependientes(
                    "No se puede eliminar el usuario porque tiene tickets o capacitaciones futuras como instructor.",
                    dependientes);
        }

        sesiones.cerrarTodas(id);
        capacitaciones.liberarInscripciones(id, ahora);
        repositorio.deleteById(id);
        usuarioSesion.invalidarSesion(id);
    }

    // ------------------------------------------------------------------
    // Perfil propio (HU06 y HU07 sin permiso)
    // ------------------------------------------------------------------

    public UsuarioResponse perfil(UsuarioAutenticado actor) {
        return aRespuesta(buscar(actor.id()));
    }

    public UsuarioResponse actualizarPerfil(ActualizarPerfilRequest solicitud, UsuarioAutenticado actor) {
        Usuario usuario = buscar(actor.id());
        usuario.setNombre(solicitud.nombre().strip());
        boolean cambiaPassword = solicitud.nuevaPassword() != null;
        if (cambiaPassword) {
            if (solicitud.passwordActual() == null
                    || !passwordEncoder.matches(solicitud.passwordActual(), usuario.getPasswordHash())) {
                throw new ReglaNegocioException("La contraseña actual no es correcta.");
            }
            usuario.setPasswordHash(passwordEncoder.encode(solicitud.nuevaPassword()));
        }
        usuario.setFechaActualizacion(ahora());
        Usuario guardado = repositorio.save(usuario);

        if (cambiaPassword) {
            sesiones.cerrarTodas(actor.id());
        }
        usuarioSesion.invalidarSesion(actor.id());
        return aRespuesta(guardado);
    }

    // ------------------------------------------------------------------
    // Reglas
    // ------------------------------------------------------------------

    /** 422 si un rol no existe, está inactivo o los roles mezclan alcances. Quita repetidos. */
    private List<RolSesion> resolverRoles(List<String> rolIds) {
        List<RolSesion> resueltos = new ArrayList<>();
        for (ObjectId rolId : rolIds.stream().map(Ids::deCuerpo).distinct().toList()) {
            RolSesion rol = roles.buscarParaSesion(rolId)
                    .orElseThrow(() -> new ReglaNegocioException("El rol " + rolId + " no existe."));
            if (!rol.activo()) {
                throw new ReglaNegocioException("El rol '" + rol.nombre() + "' está inactivo y no se puede asignar.");
            }
            resueltos.add(rol);
        }
        if (resueltos.stream().map(RolSesion::alcance).distinct().count() > 1) {
            throw new ReglaNegocioException("Todos los roles de un usuario deben tener el mismo alcance.");
        }
        return resueltos;
    }

    /** Roles actuales de un usuario, sin los que ya no existen. */
    private List<RolSesion> rolesExistentes(List<ObjectId> rolIds) {
        return rolIds.stream().map(roles::buscarParaSesion).flatMap(Optional::stream).toList();
    }

    /**
     * 403 si algún rol tiene permisos que el actor no tiene, o si un actor de empresa toca un rol
     * GLOBAL. Los permisos de autoservicio ({@link Permiso#AUTOSERVICIO}) no se exigen. El detalle
     * nombra los permisos que faltan.
     */
    private static void verificarAntiEscalada(UsuarioAutenticado actor, List<RolSesion> rolesRevisados) {
        if (actor.esDeEmpresa() && rolesRevisados.stream().anyMatch(r -> r.alcance() == AlcanceRol.GLOBAL)) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "Un usuario de empresa solo puede gestionar usuarios con roles de alcance EMPRESA.");
        }
        Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
        rolesRevisados.forEach(r -> permisos.addAll(r.permisos()));
        Set<Permiso> faltantes = Permiso.faltantesParaAsignar(permisos, actor.permisos());
        if (!faltantes.isEmpty()) {
            throw new ApiException(CodigoError.ACCESO_DENEGADO,
                    "No puede gestionar un usuario con permisos que usted no tiene: "
                            + faltantes.stream().map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
    }

    /** {@code limites.maxUsuarios}: exige membresía y un cupo libre entre los usuarios activos. */
    private void verificarCupoDeUsuarios(ObjectId empresaId) {
        PlanResponse plan = membresias.planDeEmpresa(empresaId).orElseThrow(() -> new ReglaNegocioException(
                "La empresa no tiene membresía. Asígnele una antes de registrar o reactivar usuarios."));
        int maximo = plan.limites().maxUsuarios();
        long activos = repositorio.countByEmpresaIdAndActivoTrue(empresaId);
        if (activos >= maximo) {
            throw new ReglaNegocioException("El plan '" + plan.nombre() + "' permite como máximo " + maximo
                    + " usuario(s) activo(s) y la empresa ya tiene " + activos + ".");
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Usuario buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(UsuarioService::noExiste);
    }

    /** Para alcance EMPRESA, un usuario de otra empresa (o GLOBAL) responde 404. */
    private Usuario buscarVisible(ObjectId id, UsuarioAutenticado actor) {
        Usuario usuario = buscar(id);
        if (actor.esDeEmpresa() && !actor.empresaId().equals(usuario.getEmpresaId())) {
            throw noExiste();
        }
        return usuario;
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe el usuario solicitado.");
    }

    private static ObjectId idOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : Ids.deCuerpo(valor);
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private UsuarioResponse aRespuesta(Usuario u) {
        List<UsuarioResponse.RolResumen> resumen = rolesExistentes(u.getRolIds()).stream()
                .map(r -> new UsuarioResponse.RolResumen(r.id().toHexString(), r.nombre(), r.alcance().name(),
                        r.activo()))
                .toList();
        return new UsuarioResponse(
                u.getId().toHexString(),
                u.getNombre(),
                u.getEmail(),
                u.getEmpresaId() == null ? null : u.getEmpresaId().toHexString(),
                resumen,
                Boolean.TRUE.equals(u.getActivo()),
                u.getFechaCreacion(),
                u.getFechaActualizacion());
    }
}
