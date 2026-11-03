import { useId, type ComponentProps, type ReactNode } from 'react'

import { cn } from '@/lib/cn'

type Comunes = {
  etiqueta: string
  /** Mensaje de error (por ejemplo, el de react-hook-form o el campo de "errores" del backend). */
  error?: string
  ayuda?: ReactNode
  obligatorio?: boolean
}

const CONTROL =
  'w-full rounded-control border bg-superficie px-3 text-sm text-texto placeholder:text-texto-suave/70 ' +
  'transition-colors focus:border-primario focus:outline-none focus:ring-2 focus:ring-foco/30 ' +
  'disabled:cursor-not-allowed disabled:bg-superficie-2 disabled:opacity-70'

function Envoltura({
  id,
  etiqueta,
  error,
  ayuda,
  obligatorio,
  children,
}: Comunes & { id: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-texto">
        {etiqueta}
        {obligatorio && (
          <span className="ml-0.5 text-peligro-texto" aria-hidden>
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p id={`${id}-msg`} role="alert" className="text-xs font-medium text-peligro-texto">
          {error}
        </p>
      ) : ayuda ? (
        <p id={`${id}-msg`} className="text-xs text-texto-suave">
          {ayuda}
        </p>
      ) : null}
    </div>
  )
}

function atributos(id: string, c: Comunes) {
  return {
    id,
    required: c.obligatorio,
    'aria-invalid': c.error ? true : undefined,
    'aria-describedby': c.error || c.ayuda ? `${id}-msg` : undefined,
  }
}

/** Campo de texto con etiqueta, ayuda y error. Acepta el ref de register() de react-hook-form (React 19). */
export function CampoTexto({ etiqueta, error, ayuda, obligatorio, id, className, ...props }: ComponentProps<'input'> & Comunes) {
  const generado = useId()
  const campoId = id ?? generado
  const c = { etiqueta, error, ayuda, obligatorio }
  return (
    <Envoltura id={campoId} {...c}>
      <input
        {...atributos(campoId, c)}
        className={cn(CONTROL, 'h-10', error ? 'border-peligro' : 'border-borde', className)}
        {...props}
      />
    </Envoltura>
  )
}

export function AreaTexto({ etiqueta, error, ayuda, obligatorio, id, className, rows = 4, ...props }: ComponentProps<'textarea'> & Comunes) {
  const generado = useId()
  const campoId = id ?? generado
  const c = { etiqueta, error, ayuda, obligatorio }
  return (
    <Envoltura id={campoId} {...c}>
      <textarea
        rows={rows}
        {...atributos(campoId, c)}
        className={cn(CONTROL, 'py-2', error ? 'border-peligro' : 'border-borde', className)}
        {...props}
      />
    </Envoltura>
  )
}

export type Opcion = { valor: string; etiqueta: string }

/** Lista desplegable nativa: accesible con teclado y lectores de pantalla sin código extra. */
export function Selector({
  etiqueta,
  error,
  ayuda,
  obligatorio,
  id,
  className,
  opciones,
  vacio,
  ...props
}: ComponentProps<'select'> & Comunes & { opciones: Opcion[]; vacio?: string }) {
  const generado = useId()
  const campoId = id ?? generado
  const c = { etiqueta, error, ayuda, obligatorio }
  return (
    <Envoltura id={campoId} {...c}>
      <select
        {...atributos(campoId, c)}
        className={cn(CONTROL, 'h-10 pr-8', error ? 'border-peligro' : 'border-borde', className)}
        {...props}
      >
        {vacio !== undefined && <option value="">{vacio}</option>}
        {opciones.map((o) => (
          <option key={o.valor} value={o.valor}>
            {o.etiqueta}
          </option>
        ))}
      </select>
    </Envoltura>
  )
}

export function Casilla({ etiqueta, id, className, ...props }: ComponentProps<'input'> & { etiqueta: string }) {
  const generado = useId()
  const campoId = id ?? generado
  return (
    <label htmlFor={campoId} className={cn('inline-flex cursor-pointer items-center gap-2 text-sm text-texto', className)}>
      <input id={campoId} type="checkbox" className="size-4 rounded accent-primario" {...props} />
      {etiqueta}
    </label>
  )
}
