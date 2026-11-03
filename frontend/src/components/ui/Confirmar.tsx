import type { ReactNode } from 'react'

import { mensajeEliminar } from '@/api/errores'

import { Aviso } from './Aviso'
import { Boton } from './Boton'
import { Dialogo } from './Dialogo'

/** Confirmación de una acción que no se puede deshacer (eliminar, cancelar, rechazar). */
export function Confirmar({
  abierto,
  onCerrar,
  onConfirmar,
  titulo,
  children,
  cargando,
  error,
  textoConfirmar = 'Eliminar',
}: {
  abierto: boolean
  onCerrar: () => void
  onConfirmar: () => void
  titulo: ReactNode
  children?: ReactNode
  cargando?: boolean
  error?: unknown
  textoConfirmar?: string
}) {
  return (
    <Dialogo
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={titulo}
      acciones={
        <>
          <Boton variante="secundario" onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton variante="peligro" cargando={cargando} onClick={onConfirmar}>
            {textoConfirmar}
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-3 text-sm text-texto-suave">
        {children}
        {error != null && <Aviso tono="peligro">{mensajeEliminar(error)}</Aviso>}
      </div>
    </Dialogo>
  )
}
