import { cn } from '@/lib/cn'

export type Barra = { clave: string; etiqueta: string; valor: number; texto: string; color?: string; detalle?: string }

/**
 * Barras horizontales para comparar magnitudes (tarea 3.6). Cada barra lleva su etiqueta y su valor
 * en texto, así el color nunca es la única pista; al pasar el mouse o enfocarla muestra el detalle.
 * Barras delgadas con el extremo redondeado y separación entre ellas.
 */
export function Barras({ datos, titulo }: { datos: Barra[]; titulo: string }) {
  const max = Math.max(1, ...datos.map((d) => d.valor))
  return (
    <figure className="flex flex-col gap-2.5">
      <figcaption className="sr-only">{titulo}</figcaption>
      {datos.map((d) => (
        <div
          key={d.clave}
          tabIndex={0}
          title={d.detalle ?? `${d.etiqueta}: ${d.texto}`}
          aria-label={d.detalle ?? `${d.etiqueta}: ${d.texto}`}
          className="group grid grid-cols-[minmax(7rem,11rem)_1fr_auto] items-center gap-3 rounded-control px-1 py-0.5 text-sm outline-none hover:bg-superficie-2 focus-visible:bg-superficie-2"
        >
          <span className="truncate text-texto-suave">{d.etiqueta}</span>
          <span className="h-3 w-full" aria-hidden>
            <span
              className={cn('block h-full rounded-r-[4px] transition-opacity group-hover:opacity-85', d.color ?? 'bg-primario')}
              style={{ width: `${Math.max(d.valor > 0 ? 1.5 : 0, (d.valor / max) * 100)}%` }}
            />
          </span>
          <span className="text-right font-medium tabular-nums">{d.texto}</span>
        </div>
      ))}
    </figure>
  )
}

/** Columnas verticales para una serie en el tiempo (meses). Un solo color: es una magnitud. */
export function Columnas({ datos, titulo }: { datos: Barra[]; titulo: string }) {
  const max = Math.max(1, ...datos.map((d) => d.valor))
  return (
    <figure>
      <figcaption className="sr-only">{titulo}</figcaption>
      <div className="flex h-48 items-end gap-[2px] border-b border-borde">
        {datos.map((d) => (
          <div
            key={d.clave}
            tabIndex={0}
            title={d.detalle ?? `${d.etiqueta}: ${d.texto}`}
            aria-label={d.detalle ?? `${d.etiqueta}: ${d.texto}`}
            className="group relative flex h-full flex-1 items-end outline-none"
          >
            <span
              className="block w-full rounded-t-[4px] bg-primario transition-opacity group-hover:opacity-80 group-focus-visible:opacity-80"
              style={{ height: `${Math.max(d.valor > 0 ? 1 : 0, (d.valor / max) * 100)}%` }}
            />
            <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 rounded-control border border-borde bg-superficie px-2 py-1 text-xs whitespace-nowrap shadow-lg group-hover:block group-focus-visible:block">
              {d.detalle ?? `${d.etiqueta}: ${d.texto}`}
            </span>
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-[2px] text-[11px] text-texto-suave">
        {datos.map((d) => (
          <span key={d.clave} className="flex-1 truncate text-center">{d.etiqueta}</span>
        ))}
      </div>
    </figure>
  )
}
