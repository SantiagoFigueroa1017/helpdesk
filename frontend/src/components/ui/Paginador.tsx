import { ChevronLeft, ChevronRight } from 'lucide-react'

import { formato } from '@/lib/dominio'

import { Boton } from './Boton'

/** Pie de un listado paginado (Pagina<T> del backend, contexto 8.2). La página empieza en 0. */
export function Paginador({
  pagina,
  totalPaginas,
  totalElementos,
  onCambiar,
}: {
  pagina: number
  totalPaginas: number
  totalElementos: number
  onCambiar: (pagina: number) => void
}) {
  if (totalElementos === 0) return null
  return (
    <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3 border-t border-borde px-4 py-3 text-sm">
      <p className="text-texto-suave">
        {formato.numero(totalElementos)} {totalElementos === 1 ? 'resultado' : 'resultados'} · página {pagina + 1} de{' '}
        {Math.max(1, totalPaginas)}
      </p>
      <div className="flex gap-2">
        <Boton
          variante="secundario"
          tamano="sm"
          disabled={pagina === 0}
          onClick={() => onCambiar(pagina - 1)}
          icono={<ChevronLeft aria-hidden className="size-4" />}
        >
          Anterior
        </Boton>
        <Boton variante="secundario" tamano="sm" disabled={pagina + 1 >= totalPaginas} onClick={() => onCambiar(pagina + 1)}>
          Siguiente
          <ChevronRight aria-hidden className="size-4" />
        </Boton>
      </div>
    </nav>
  )
}
