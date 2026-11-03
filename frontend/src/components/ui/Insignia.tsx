import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'
import type { Tono } from '@/lib/dominio'

const TONOS: Record<Tono, string> = {
  neutro: 'bg-superficie-2 text-texto-suave',
  primario: 'bg-primario-suave text-sobre-primario-suave',
  exito: 'bg-exito-fondo text-exito-texto',
  aviso: 'bg-aviso-fondo text-aviso-texto',
  peligro: 'bg-peligro-fondo text-peligro-texto',
  info: 'bg-info-fondo text-info-texto',
}

/** Etiqueta corta de estado o categoría. El color nunca va solo: siempre lleva texto. */
export function Insignia({ tono = 'neutro', punto, children }: { tono?: Tono; punto?: string; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap', TONOS[tono])}>
      {punto && <span aria-hidden className={cn('size-1.5 rounded-full', punto)} />}
      {children}
    </span>
  )
}
