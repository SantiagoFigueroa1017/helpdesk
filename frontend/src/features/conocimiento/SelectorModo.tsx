import { Search, Sparkles } from 'lucide-react'

import { cn } from '@/lib/cn'

import type { ModoBusqueda } from './busqueda'

const MODOS = [
  ['texto', 'Por palabras', Search],
  ['significado', 'Por significado', Sparkles],
] as const

/** Interruptor entre la búsqueda por palabras y la búsqueda por significado. */
export function SelectorModo({ modo, onCambiar }: { modo: ModoBusqueda; onCambiar: (m: ModoBusqueda) => void }) {
  return (
    <div role="radiogroup" aria-label="Tipo de búsqueda" className="inline-flex w-fit rounded-control border border-borde p-0.5">
      {MODOS.map(([valor, etiqueta, Icono]) => (
        <button
          key={valor}
          type="button"
          role="radio"
          aria-checked={modo === valor}
          onClick={() => onCambiar(valor)}
          className={cn(
            'inline-flex cursor-pointer items-center gap-1.5 rounded-[0.4rem] px-3 py-1.5 text-sm font-medium',
            modo === valor ? 'bg-primario text-sobre-primario' : 'text-texto-suave hover:text-texto',
          )}
        >
          <Icono aria-hidden className="size-4" />
          {etiqueta}
        </button>
      ))}
    </div>
  )
}
