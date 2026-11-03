package co.edu.ufpso.helpdesk.common.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Catálogo fijo de los 49 permisos (modelo 1.2, sección "Catálogo de permisos"; contexto 8.4).
 *
 * <p>Cada permiso corresponde a código programado, por eso el catálogo vive en el backend y no se
 * crean permisos desde la interfaz. El nombre de cada constante es el código que se guarda en
 * {@code roles.permisos} y el que se usa en {@code @PreAuthorize("hasAuthority('...')")}.
 *
 * <p>El validador de {@code roles} ({@code database/validadores/02_roles.js}) tiene la misma
 * lista. {@code RolPersistenciaTest} comprueba que las dos coinciden: si se agrega un
 * permiso, hay que cambiar los dos lados.
 *
 * <p>Cada permiso trae su grupo y una descripción en español (acta 2.4) para que la pantalla de
 * roles (HU26) muestre "Crear tickets" y no {@code TICKETS_CREAR}. No se guardan en MongoDB.
 *
 * <p>Está en {@code common} porque lo usan {@code roles}, {@code usuarios} (anti-escalada) y
 * {@code security}, y un módulo no puede usar el {@code domain} de otro.
 */
public enum Permiso {
    EMPRESAS_CREAR("Empresas", "Registrar empresas clientes"),
    EMPRESAS_EDITAR("Empresas", "Editar los datos de cualquier empresa"),
    EMPRESAS_ELIMINAR("Empresas", "Eliminar empresas sin dependientes"),
    EMPRESAS_EDITAR_CONTACTO("Empresas", "Editar el contacto de la propia empresa"),
    EMPRESAS_VER("Empresas", "Consultar empresas"),
    USUARIOS_CREAR("Usuarios", "Registrar usuarios"),
    USUARIOS_EDITAR("Usuarios", "Editar usuarios y sus roles"),
    USUARIOS_ELIMINAR("Usuarios", "Eliminar usuarios sin dependientes"),
    USUARIOS_VER("Usuarios", "Consultar usuarios"),
    PLANES_CREAR("Planes", "Crear planes"),
    PLANES_EDITAR("Planes", "Editar planes"),
    PLANES_ELIMINAR("Planes", "Eliminar planes sin membresías"),
    PLANES_VER("Planes", "Consultar planes"),
    MEMBRESIAS_CREAR("Membresías", "Asignar membresías"),
    MEMBRESIAS_EDITAR("Membresías", "Editar membresías"),
    MEMBRESIAS_ELIMINAR("Membresías", "Eliminar membresías sin pagos"),
    MEMBRESIAS_VER("Membresías", "Consultar membresías"),
    PAGOS_REGISTRAR("Pagos", "Registrar pagos"),
    PAGOS_EDITAR("Pagos", "Editar pagos"),
    PAGOS_ELIMINAR("Pagos", "Eliminar pagos"),
    PAGOS_VER("Pagos", "Consultar pagos"),
    TICKETS_CREAR("Tickets", "Crear tickets"),
    TICKETS_VER("Tickets", "Ver todos los tickets de su alcance"),
    TICKETS_VER_PROPIOS("Tickets", "Ver los tickets donde es solicitante"),
    TICKETS_EDITAR("Tickets", "Editar cualquier ticket"),
    TICKETS_EDITAR_PROPIOS("Tickets", "Editar sus tickets abiertos y sin asignar"),
    TICKETS_ASIGNAR("Tickets", "Asignarse tickets sin asignar"),
    TICKETS_ASIGNAR_OTROS("Tickets", "Asignar o reasignar tickets a cualquier agente"),
    TICKETS_ELIMINAR("Tickets", "Eliminar tickets"),
    COMENTARIOS_CREAR("Comentarios", "Comentar los tickets que puede ver"),
    COMENTARIOS_ELIMINAR("Comentarios", "Eliminar comentarios de cualquier autor"),
    ROLES_VER("Roles", "Consultar roles y el catálogo de permisos"),
    ROLES_GESTIONAR("Roles", "Crear, editar y eliminar roles"),
    ARTICULOS_VER("Base de conocimientos", "Consultar artículos públicos"),
    ARTICULOS_VER_INTERNOS("Base de conocimientos", "Consultar también los artículos internos"),
    ARTICULOS_CREAR("Base de conocimientos", "Crear artículos"),
    ARTICULOS_EDITAR("Base de conocimientos", "Editar artículos"),
    ARTICULOS_ELIMINAR("Base de conocimientos", "Eliminar artículos"),
    CAPACITACIONES_VER("Capacitaciones", "Consultar capacitaciones"),
    CAPACITACIONES_CREAR("Capacitaciones", "Programar capacitaciones"),
    CAPACITACIONES_EDITAR("Capacitaciones", "Editar capacitaciones"),
    CAPACITACIONES_ELIMINAR("Capacitaciones", "Eliminar capacitaciones"),
    CAPACITACIONES_SOLICITAR("Capacitaciones", "Solicitar capacitaciones personalizadas para su empresa"),
    SOLICITUDES_GESTIONAR("Capacitaciones", "Programar o rechazar solicitudes de capacitación"),
    INSCRIPCIONES_PROPIAS("Inscripciones", "Inscribirse y cancelar sus inscripciones"),
    INSCRIPCIONES_GESTIONAR("Inscripciones", "Inscribir o cancelar a otros usuarios"),
    INSCRIPCIONES_VER("Inscripciones", "Ver las listas de inscritos"),
    ASISTENCIA_MARCAR("Inscripciones", "Marcar asistencia"),
    REPORTES_VER("Reportes", "Ver los reportes de analítica");

    /**
     * Permisos de autoservicio (decidido en 2.5): solo actúan sobre los datos del propio usuario
     * (sus tickets como solicitante, editarlos mientras nadie los atiende, inscribirse él mismo),
     * así que darlos no permite ver ni tocar datos de nadie más. La anti-escalada al asignar roles a
     * usuarios no los exige al actor: un CLIENTE_ADMIN puede asignar el rol CLIENTE aunque él no
     * tenga {@code TICKETS_VER_PROPIOS} ni {@code INSCRIPCIONES_PROPIAS}.
     */
    public static final Set<Permiso> AUTOSERVICIO = Set.copyOf(EnumSet.of(
            TICKETS_VER_PROPIOS, TICKETS_EDITAR_PROPIOS, INSCRIPCIONES_PROPIAS));

    /**
     * Los permisos de {@code permisos} que un actor con {@code delActor} no podría asignar: los que
     * no tiene, sin contar los de autoservicio. Vacío si puede asignarlos todos.
     */
    public static Set<Permiso> faltantesParaAsignar(Set<Permiso> permisos, Set<Permiso> delActor) {
        Set<Permiso> faltantes = EnumSet.noneOf(Permiso.class);
        faltantes.addAll(permisos);
        faltantes.removeAll(delActor);
        faltantes.removeAll(AUTOSERVICIO);
        return faltantes;
    }

    private final String grupo;
    private final String descripcion;

    Permiso(String grupo, String descripcion) {
        this.grupo = grupo;
        this.descripcion = descripcion;
    }

    /** Agrupa los permisos en la pantalla de roles, por ejemplo "Tickets". */
    public String getGrupo() {
        return grupo;
    }

    /** Texto para la pantalla de roles, por ejemplo "Crear tickets". */
    public String getDescripcion() {
        return descripcion;
    }
}
