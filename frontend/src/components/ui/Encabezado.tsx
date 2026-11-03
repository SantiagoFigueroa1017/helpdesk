import type { ReactNode } from 'react'

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

/** Fila de filtros encima de una tabla. */
export function Filtros({ children }: { children: ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{children}</div>
}
