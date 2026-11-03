import {
  BarChart3,
  BookOpen,
  Building2,
  CalendarCheck,
  ClipboardList,
  CreditCard,
  GraduationCap,
  House,
  Inbox,
  Layers,
  ShieldCheck,
  Ticket,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Permiso } from '@/features/auth/permisos'

/**
 * Menú y rutas protegidas de la aplicación (tarea 3.2). Cada entrada dice qué permisos la habilitan:
 * basta con tener uno. Se decide por permiso, nunca por nombre de rol (contexto 8.4), así un rol
 * nuevo creado desde HU25 ve exactamente lo que sus permisos le dejan.
 *
 * Los permisos salen de la matriz (modelo/MATRIZ_PERMISOS_2.4.md): es el permiso del endpoint de
 * consulta de cada módulo. La tarea indica cuándo se construye la pantalla.
 */
export type EntradaMenu = {
  ruta: string
  etiqueta: string
  icono: LucideIcon
  permisos: Permiso[]
  tarea: string
  /** Etiqueta para un usuario de empresa, si cambia (por ejemplo "Mi empresa"). */
  etiquetaEmpresa?: string
}

export type SeccionMenu = { titulo: string; entradas: EntradaMenu[] }

export const INICIO: EntradaMenu = { ruta: '/', etiqueta: 'Inicio', icono: House, permisos: [], tarea: '3.2' }

export const MENU: SeccionMenu[] = [
  {
    titulo: 'Soporte',
    entradas: [
      { ruta: '/tickets', etiqueta: 'Tickets', icono: Ticket, permisos: ['TICKETS_VER', 'TICKETS_VER_PROPIOS'], tarea: '3.3' },
      { ruta: '/conocimiento', etiqueta: 'Base de conocimientos', icono: BookOpen, permisos: ['ARTICULOS_VER'], tarea: '3.4' },
    ],
  },
  {
    titulo: 'Capacitación',
    entradas: [
      { ruta: '/capacitaciones', etiqueta: 'Capacitaciones', icono: GraduationCap, permisos: ['CAPACITACIONES_VER'], tarea: '3.5' },
      {
        ruta: '/solicitudes',
        etiqueta: 'Solicitudes',
        icono: Inbox,
        permisos: ['CAPACITACIONES_SOLICITAR', 'SOLICITUDES_GESTIONAR'],
        tarea: '3.5',
      },
      { ruta: '/mis-inscripciones', etiqueta: 'Mis inscripciones', icono: CalendarCheck, permisos: ['INSCRIPCIONES_PROPIAS'], tarea: '3.5' },
    ],
  },
  {
    titulo: 'Clientes',
    entradas: [
      { ruta: '/empresas', etiqueta: 'Empresas', etiquetaEmpresa: 'Mi empresa', icono: Building2, permisos: ['EMPRESAS_VER'], tarea: '3.5' },
      { ruta: '/usuarios', etiqueta: 'Usuarios', icono: Users, permisos: ['USUARIOS_VER'], tarea: '3.5' },
      { ruta: '/planes', etiqueta: 'Planes', icono: Layers, permisos: ['PLANES_VER'], tarea: '3.5' },
      { ruta: '/membresias', etiqueta: 'Membresías', etiquetaEmpresa: 'Mi membresía', icono: ClipboardList, permisos: ['MEMBRESIAS_VER'], tarea: '3.5' },
      { ruta: '/pagos', etiqueta: 'Pagos', icono: CreditCard, permisos: ['PAGOS_VER'], tarea: '3.5' },
    ],
  },
  {
    titulo: 'Administración',
    entradas: [
      { ruta: '/roles', etiqueta: 'Roles y permisos', icono: ShieldCheck, permisos: ['ROLES_VER'], tarea: '3.5' },
      { ruta: '/reportes', etiqueta: 'Reportes', icono: BarChart3, permisos: ['REPORTES_VER'], tarea: '3.6' },
    ],
  },
]

export const ENTRADAS: EntradaMenu[] = [INICIO, ...MENU.flatMap((s) => s.entradas)]
