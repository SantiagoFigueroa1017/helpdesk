import type { ComponentProps, ReactNode } from 'react'
import { LoaderCircle } from 'lucide-react'

import { cn } from '@/lib/cn'

type Variante = 'primario' | 'secundario' | 'fantasma' | 'peligro'
type Tamano = 'sm' | 'md'

const VARIANTES: Record<Variante, string> = {
  primario: 'bg-primario text-sobre-primario hover:bg-primario-hover',
  secundario: 'border border-borde bg-superficie text-texto hover:bg-superficie-2',
  fantasma: 'text-texto-suave hover:bg-superficie-2 hover:text-texto',
  peligro: 'bg-peligro text-white hover:bg-peligro-hover',
}

const TAMANOS: Record<Tamano, string> = {
  sm: 'h-8 gap-1.5 px-3 text-xs',
  md: 'h-10 gap-2 px-4 text-sm',
}

export type BotonProps = ComponentProps<'button'> & {
  variante?: Variante
  tamano?: Tamano
  /** Muestra un indicador y desactiva el botón mientras la petición está en curso. */
  cargando?: boolean
  icono?: ReactNode
}

/** Botón del sistema. Por defecto es type="button" para que no envíe formularios sin querer. */
export function Boton({
  variante = 'primario',
  tamano = 'md',
  cargando = false,
  icono,
  type = 'button',
  disabled,
  className,
  children,
  ...props
}: BotonProps) {
  return (
    <button
      type={type}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      className={cn(
        'inline-flex shrink-0 cursor-pointer items-center justify-center rounded-control font-semibold whitespace-nowrap transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-55',
        VARIANTES[variante],
        TAMANOS[tamano],
        className,
      )}
      {...props}
    >
      {cargando ? <LoaderCircle aria-hidden className="size-4 animate-spin" /> : icono}
      {children}
    </button>
  )
}
