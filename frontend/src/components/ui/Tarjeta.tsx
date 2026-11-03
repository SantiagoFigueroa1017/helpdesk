import type { ComponentProps, ReactNode } from 'react'

import { cn } from '@/lib/cn'

/** Contenedor blanco con borde. Con titulo, agrega una cabecera con acciones a la derecha. */
export function Tarjeta({
  titulo,
  descripcion,
  acciones,
  sinRelleno = false,
  className,
  children,
  ...props
}: ComponentProps<'section'> & { titulo?: ReactNode; descripcion?: ReactNode; acciones?: ReactNode; sinRelleno?: boolean }) {
  return (
    <section className={cn('rounded-tarjeta border border-borde bg-superficie shadow-tarjeta', className)} {...props}>
      {(titulo || acciones) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-borde px-4 py-3 sm:px-5">
          <div>
            {titulo && <h2 className="text-base font-semibold text-texto">{titulo}</h2>}
            {descripcion && <p className="mt-0.5 text-sm text-texto-suave">{descripcion}</p>}
          </div>
          {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
        </header>
      )}
      <div className={sinRelleno ? undefined : 'p-4 sm:p-5'}>{children}</div>
    </section>
  )
}

/** Cifra destacada para los indicadores (KPI) del panel. */
export function Estadistica({ etiqueta, valor, detalle }: { etiqueta: string; valor: ReactNode; detalle?: ReactNode }) {
  return (
    <div className="rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta">
      <p className="text-sm text-texto-suave">{etiqueta}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight text-texto tabular-nums">{valor}</p>
      {detalle && <p className="mt-1 text-xs text-texto-suave">{detalle}</p>}
    </div>
  )
}
