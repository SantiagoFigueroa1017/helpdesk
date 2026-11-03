package co.edu.ufpso.helpdesk.common.dominio;

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
 * <p>Está en {@code common} porque lo usan {@code roles}, {@code usuarios} (anti-escalada) y
 * {@code security}, y un módulo no puede usar el {@code domain} de otro.
 */
public enum Permiso {
    EMPRESAS_CREAR, EMPRESAS_EDITAR, EMPRESAS_ELIMINAR, EMPRESAS_EDITAR_CONTACTO, EMPRESAS_VER,
    USUARIOS_CREAR, USUARIOS_EDITAR, USUARIOS_ELIMINAR, USUARIOS_VER,
    PLANES_CREAR, PLANES_EDITAR, PLANES_ELIMINAR, PLANES_VER,
    MEMBRESIAS_CREAR, MEMBRESIAS_EDITAR, MEMBRESIAS_ELIMINAR, MEMBRESIAS_VER,
    PAGOS_REGISTRAR, PAGOS_EDITAR, PAGOS_ELIMINAR, PAGOS_VER,
    TICKETS_CREAR, TICKETS_VER, TICKETS_VER_PROPIOS, TICKETS_EDITAR, TICKETS_EDITAR_PROPIOS,
    TICKETS_ASIGNAR, TICKETS_ASIGNAR_OTROS, TICKETS_ELIMINAR,
    COMENTARIOS_CREAR, COMENTARIOS_ELIMINAR,
    ROLES_VER, ROLES_GESTIONAR,
    ARTICULOS_VER, ARTICULOS_VER_INTERNOS, ARTICULOS_CREAR, ARTICULOS_EDITAR, ARTICULOS_ELIMINAR,
    CAPACITACIONES_VER, CAPACITACIONES_CREAR, CAPACITACIONES_EDITAR, CAPACITACIONES_ELIMINAR,
    CAPACITACIONES_SOLICITAR,
    SOLICITUDES_GESTIONAR,
    INSCRIPCIONES_PROPIAS, INSCRIPCIONES_GESTIONAR, INSCRIPCIONES_VER,
    ASISTENCIA_MARCAR,
    REPORTES_VER
}
