import { Compass, Construction, Home, ShieldOff, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

/** Página con un mensaje centrado: base de "en construcción", "sin acceso", 404 y error. */
function Mensaje({ icono, titulo, children, accion = true }: { icono: ReactNode; titulo: string; children?: ReactNode; accion?: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-primario-suave text-sobre-primario-suave">{icono}</span>
      <h1 className="text-xl font-semibold">{titulo}</h1>
      {children && <div className="max-w-md text-sm text-texto-suave">{children}</div>}
      {accion && (
        <Link
          to="/"
          className="mt-2 inline-flex h-10 items-center gap-2 rounded-control border border-borde bg-superficie px-4 text-sm font-semibold hover:bg-superficie-2"
        >
          <Home aria-hidden className="size-4" />
          Ir al inicio
        </Link>
      )}
    </div>
  )
}

/** Módulo con menú y permisos listos, pero cuya pantalla llega en una tarea posterior. */
export function EnConstruccion({ titulo, tarea }: { titulo: string; tarea: string }) {
  return (
    <Mensaje icono={<Construction aria-hidden className="size-6" />} titulo={titulo}>
      Esta pantalla se construye en la tarea {tarea}. Tus permisos ya te dan acceso a ella.
    </Mensaje>
  )
}

export function SinAcceso() {
  return (
    <Mensaje icono={<ShieldOff aria-hidden className="size-6" />} titulo="No tienes acceso a esta sección">
      Tus roles no incluyen el permiso para verla. Si lo necesitas, pídeselo al administrador.
    </Mensaje>
  )
}

export function NoEncontrada() {
  return (
    <Mensaje icono={<Compass aria-hidden className="size-6" />} titulo="Página no encontrada">
      La dirección no existe o cambió.
    </Mensaje>
  )
}

/** Error inesperado al mostrar una ruta (errorElement del router). */
export function PaginaError() {
  const error = useRouteError()
  const detalle = isRouteErrorResponse(error)
    ? `${error.status} · ${error.statusText}`
    : error instanceof Error
      ? error.message
      : undefined
  return (
    <div className="px-4">
      <Mensaje icono={<TriangleAlert aria-hidden className="size-6" />} titulo="Algo salió mal">
        <p>Recarga la página. Si el problema sigue, avísale al equipo con este detalle:</p>
        {detalle && <p className="mt-2 font-mono text-xs break-all">{detalle}</p>}
      </Mensaje>
    </div>
  )
}
