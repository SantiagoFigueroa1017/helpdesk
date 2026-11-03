import type { ReactNode } from 'react'
import { createBrowserRouter } from 'react-router'

import { PaginaLogin } from '@/features/auth/PaginaLogin'
import type { Permiso } from '@/features/auth/permisos'
import { PaginaCapacitaciones } from '@/features/capacitaciones/PaginaCapacitaciones'
import { PaginaMisInscripciones } from '@/features/capacitaciones/PaginaMisInscripciones'
import { PaginaSolicitudes } from '@/features/capacitaciones/PaginaSolicitudes'
import { PaginaArticulo } from '@/features/conocimiento/PaginaArticulo'
import { PaginaConocimiento } from '@/features/conocimiento/PaginaConocimiento'
import { PaginaEmpresas } from '@/features/empresas/PaginaEmpresas'
import { PaginaInicio } from '@/features/inicio/PaginaInicio'
import { PaginaMembresias } from '@/features/membresias/PaginaMembresias'
import { PaginaPagos } from '@/features/pagos/PaginaPagos'
import { PaginaPerfil } from '@/features/perfil/PaginaPerfil'
import { PaginaPlanes } from '@/features/planes/PaginaPlanes'
import { PaginaReportes } from '@/features/reportes/PaginaReportes'
import { PaginaRoles } from '@/features/roles/PaginaRoles'
import { PaginaTicket } from '@/features/tickets/PaginaTicket'
import { PaginaTicketNuevo } from '@/features/tickets/PaginaTicketNuevo'
import { PaginaTickets } from '@/features/tickets/PaginaTickets'
import { PaginaUsuarios } from '@/features/usuarios/PaginaUsuarios'

import { GaleriaDiseno } from './GaleriaDiseno'
import { RequierePermiso, RutaConSesion } from './Guardias'
import { MENU } from './navegacion'
import { EnConstruccion, NoEncontrada, PaginaError } from './paginas'

/**
 * Rutas de la aplicación (tareas 3.2 a 3.6). Las de cada módulo salen del menú (navegacion.ts) con
 * su permiso; PAGINAS dice qué pantalla muestra cada una. Las rutas de detalle (un ticket, un
 * artículo) piden el mismo permiso que su listado.
 */
const PAGINAS: Record<string, ReactNode> = {
  '/tickets': <PaginaTickets />,
  '/conocimiento': <PaginaConocimiento />,
  '/capacitaciones': <PaginaCapacitaciones />,
  '/solicitudes': <PaginaSolicitudes />,
  '/mis-inscripciones': <PaginaMisInscripciones />,
  '/empresas': <PaginaEmpresas />,
  '/usuarios': <PaginaUsuarios />,
  '/planes': <PaginaPlanes />,
  '/membresias': <PaginaMembresias />,
  '/pagos': <PaginaPagos />,
  '/roles': <PaginaRoles />,
  '/reportes': <PaginaReportes />,
}

const protegida = (path: string, permisos: Permiso[], pagina: ReactNode) => ({
  path,
  element: <RequierePermiso permisos={permisos}>{pagina}</RequierePermiso>,
})

const modulos = MENU.flatMap((s) => s.entradas).map((e) =>
  protegida(e.ruta.slice(1), e.permisos, PAGINAS[e.ruta] ?? <EnConstruccion titulo={e.etiqueta} tarea={e.tarea} />),
)

export const router = createBrowserRouter([
  { path: '/login', element: <PaginaLogin />, errorElement: <PaginaError /> },
  {
    path: '/',
    element: <RutaConSesion />,
    errorElement: <PaginaError />,
    children: [
      { index: true, element: <PaginaInicio /> },
      { path: 'perfil', element: <PaginaPerfil /> },
      { path: 'diseno', element: <GaleriaDiseno /> },
      ...modulos,
      protegida('tickets/nuevo', ['TICKETS_CREAR'], <PaginaTicketNuevo />),
      protegida('tickets/:id', ['TICKETS_VER', 'TICKETS_VER_PROPIOS'], <PaginaTicket />),
      protegida('conocimiento/:id', ['ARTICULOS_VER'], <PaginaArticulo />),
      { path: '*', element: <NoEncontrada /> },
    ],
  },
])
