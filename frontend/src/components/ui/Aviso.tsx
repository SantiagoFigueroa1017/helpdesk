import type { ReactNode } from 'react'
import { CircleCheck, CircleX, Info, TriangleAlert } from 'lucide-react'

import { cn } from '@/lib/cn'

type TonoAviso = 'info' | 'exito' | 'aviso' | 'peligro'

const ESTILO: Record<TonoAviso, { clase: string; Icono: typeof Info }> = {
  info: { clase: 'bg-info-fondo text-info-texto', Icono: Info },
  exito: { clase: 'bg-exito-fondo text-exito-texto', Icono: CircleCheck },
  aviso: { clase: 'bg-aviso-fondo text-aviso-texto', Icono: TriangleAlert },
  peligro: { clase: 'bg-peligro-fondo text-peligro-texto', Icono: CircleX },
}

/**
 * Mensaje en línea. Ejemplos: membresía suspendida (403 SOPORTE_BLOQUEADO), búsqueda semántica con
 * "aviso", errores de un formulario. Los de peligro se anuncian a los lectores de pantalla.
 */
export function Aviso({ tono = 'info', titulo, children, acciones }: { tono?: TonoAviso; titulo?: ReactNode; children?: ReactNode; acciones?: ReactNode }) {
  const { clase, Icono } = ESTILO[tono]
  return (
    <div role={tono === 'peligro' ? 'alert' : 'status'} className={cn('flex gap-3 rounded-control px-4 py-3 text-sm', clase)}>
      <Icono aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div className="flex-1">
        {titulo && <p className="font-semibold">{titulo}</p>}
        {children && <div className={titulo ? 'mt-0.5' : undefined}>{children}</div>}
        {acciones && <div className="mt-2 flex flex-wrap gap-2">{acciones}</div>}
      </div>
    </div>
  )
}
