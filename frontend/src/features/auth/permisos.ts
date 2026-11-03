/**
 * Catálogo de los 49 permisos del backend (common/dominio/Permiso, modelo 1.2). El frontend decide
 * qué mostrar por permiso, nunca por el nombre del rol (contexto 8.4). Si el backend agrega un
 * permiso, se agrega aquí.
 */
export const PERMISOS = [
  'EMPRESAS_CREAR', 'EMPRESAS_EDITAR', 'EMPRESAS_ELIMINAR', 'EMPRESAS_EDITAR_CONTACTO', 'EMPRESAS_VER',
  'USUARIOS_CREAR', 'USUARIOS_EDITAR', 'USUARIOS_ELIMINAR', 'USUARIOS_VER',
  'PLANES_CREAR', 'PLANES_EDITAR', 'PLANES_ELIMINAR', 'PLANES_VER',
  'MEMBRESIAS_CREAR', 'MEMBRESIAS_EDITAR', 'MEMBRESIAS_ELIMINAR', 'MEMBRESIAS_VER',
  'PAGOS_REGISTRAR', 'PAGOS_EDITAR', 'PAGOS_ELIMINAR', 'PAGOS_VER',
  'TICKETS_CREAR', 'TICKETS_VER', 'TICKETS_VER_PROPIOS', 'TICKETS_EDITAR', 'TICKETS_EDITAR_PROPIOS',
  'TICKETS_ASIGNAR', 'TICKETS_ASIGNAR_OTROS', 'TICKETS_ELIMINAR',
  'COMENTARIOS_CREAR', 'COMENTARIOS_ELIMINAR',
  'ROLES_VER', 'ROLES_GESTIONAR',
  'ARTICULOS_VER', 'ARTICULOS_VER_INTERNOS', 'ARTICULOS_CREAR', 'ARTICULOS_EDITAR', 'ARTICULOS_ELIMINAR',
  'CAPACITACIONES_VER', 'CAPACITACIONES_CREAR', 'CAPACITACIONES_EDITAR', 'CAPACITACIONES_ELIMINAR',
  'CAPACITACIONES_SOLICITAR', 'SOLICITUDES_GESTIONAR',
  'INSCRIPCIONES_PROPIAS', 'INSCRIPCIONES_GESTIONAR', 'INSCRIPCIONES_VER', 'ASISTENCIA_MARCAR',
  'REPORTES_VER',
] as const

export type Permiso = (typeof PERMISOS)[number]

export type Alcance = 'GLOBAL' | 'EMPRESA'

/** Usuario de la sesión, tal como lo devuelven /api/auth/login y /refresh (UsuarioSesionResponse). */
export type UsuarioSesion = {
  id: string
  nombre: string
  email: string
  alcance: Alcance
  /** null para el personal de Sistemas Integrados (alcance GLOBAL). */
  empresaId: string | null
  permisos: Permiso[]
}

/** true si el usuario tiene al menos uno de los permisos. Sin permisos pedidos, siempre true. */
export function tieneAlguno(usuario: UsuarioSesion | null, permisos: readonly Permiso[]): boolean {
  if (!usuario) return false
  if (permisos.length === 0) return true
  return permisos.some((p) => usuario.permisos.includes(p))
}

export function tiene(usuario: UsuarioSesion | null, permiso: Permiso): boolean {
  return tieneAlguno(usuario, [permiso])
}
