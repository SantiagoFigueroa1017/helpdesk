import type { ComponentProps } from 'react'

import { cn } from '@/lib/cn'

/**
 * Tabla con desplazamiento horizontal en pantallas pequeñas. Se arma con etiquetas normales:
 * <Tabla><thead><tr><Th>…</Th></tr></thead><tbody><Tr><Td>…</Td></Tr></tbody></Tabla>
 */
export function Tabla({ className, children, ...props }: ComponentProps<'table'>) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn('w-full border-collapse text-left text-sm', className)} {...props}>
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
