import { useLayoutEffect, useRef, type ComponentProps } from 'react'

import { cn } from '@/lib/cn'

/**
 * Tabla de los listados. Se arma con etiquetas normales:
 * <Tabla><thead><tr><Th>…</Th></tr></thead><tbody><Tr><Td>…</Td></Tr></tbody></Tabla>
 *
 * En el celular y la tablet (menos de 1024 px, donde el menú lateral se oculta) cada fila se muestra como una tarjeta: la primera celda arriba como
 * título y las demás con el nombre de su columna al lado (tarea 3.7). Para eso la tabla copia el texto
 * de cada encabezado en el atributo data-etiqueta de las celdas de su columna, también cuando cambian
 * las filas (paginación, filtros). Los estilos están en index.css (.tabla-adaptable). Desde 1024 px es
 * una tabla normal, con desplazamiento horizontal si no cabe.
 */
export function Tabla({ className, children, ...props }: ComponentProps<'table'>) {
  const tabla = useRef<HTMLTableElement>(null)

  useLayoutEffect(() => {
    const t = tabla.current
    if (!t) return
    const etiquetar = () => {
      const encabezados = [...t.querySelectorAll('thead th')].map((th) => th.textContent?.trim() ?? '')
      t.querySelectorAll('tbody tr').forEach((fila) => {
        Array.from(fila.children).forEach((celda, i) => {
          const etiqueta = encabezados[i] ?? ''
          if (celda.getAttribute('data-etiqueta') !== etiqueta) celda.setAttribute('data-etiqueta', etiqueta)
        })
      })
    }
    etiquetar()
    const observador = new MutationObserver(etiquetar)
    observador.observe(t, { childList: true, subtree: true })
    return () => observador.disconnect()
  }, [])

  return (
    <div className="w-full overflow-x-auto">
      <table ref={tabla} className={cn('tabla-adaptable w-full border-collapse text-left text-sm', className)} {...props}>
        {children}
      </table>
    </div>
  )
}

export function Th({ className, ...props }: ComponentProps<'th'>) {
  return (
    <th
      scope="col"
      className={cn(
        'border-b border-borde bg-superficie-2 px-4 py-2.5 text-xs font-semibold tracking-wide text-texto-suave uppercase whitespace-nowrap',
        className,
      )}
      {...props}
    />
  )
}

export function Tr({ className, ...props }: ComponentProps<'tr'>) {
  return <tr className={cn('border-b border-borde last:border-0 hover:bg-superficie-2/60', className)} {...props} />
}

export function Td({ className, ...props }: ComponentProps<'td'>) {
  return <td className={cn('px-4 py-3 align-middle text-texto', className)} {...props} />
}
