import type { ReactNode } from 'react'
import { LoaderCircle } from 'lucide-react'

/** Indicador de carga con texto para lectores de pantalla. */
export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-8 text-sm text-texto-suave">
      <LoaderCircle aria-hidden className="size-5 animate-spin text-primario" />
      {texto}
    </div>
  )
}

/** Mensaje para listas sin resultados. */
export function Vacio({ titulo, children }: { titulo: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-1 py-10 text-center">
      <p className="font-medium text-texto">{titulo}</p>
      {children && <p className="max-w-sm text-sm text-texto-suave">{children}</p>}
    </div>
  )
}
