import { useState, type ReactNode } from 'react'
import { SlidersHorizontal } from 'lucide-react'

import { cn } from '@/lib/cn'

/** Título de una pantalla con su descripción y las acciones principales a la derecha. */
export function Encabezado({ titulo, descripcion, acciones }: { titulo: ReactNode; descripcion?: ReactNode; acciones?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">{titulo}</h1>
        {descripcion && <p className="mt-1 text-texto-suave">{descripcion}</p>}
      </div>
      {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
    </div>
  )
}

/**
 * Fila de filtros encima de una tabla. En el celular quedan detrás del botón "Filtros" para que el
 * listado se vea sin desplazarse (tarea 3.7); desde 640 px siempre están a la vista.
 */
export function Filtros({ children }: { children: ReactNode }) {
  const [abierto, setAbierto] = useState(false)
  return (
    <div>
      <button
        type="button"
        aria-expanded={abierto}
        onClick={() => setAbierto((a) => !a)}
        className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-control border border-borde bg-superficie px-4 text-sm font-medium text-texto hover:bg-superficie-2 sm:hidden"
      >
        <SlidersHorizontal aria-hidden className="size-4" />
        {abierto ? 'Ocultar filtros' : 'Filtros'}
      </button>
      <div className={cn('gap-3 sm:grid sm:grid-cols-2 lg:grid-cols-4', abierto ? 'mt-3 grid' : 'hidden')}>{children}</div>
    </div>
  )
}
