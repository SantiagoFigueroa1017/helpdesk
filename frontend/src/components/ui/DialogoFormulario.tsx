import { useId, type FormEvent, type ReactNode } from 'react'

import { Aviso } from './Aviso'
import { Boton } from './Boton'
import { Dialogo } from './Dialogo'

/**
 * Diálogo con un formulario. El botón de enviar vive en el pie del diálogo y se conecta al
 * formulario con el atributo form, así Enter también envía.
 */
export function DialogoFormulario({
  abierto,
  onCerrar,
  titulo,
  descripcion,
  onEnviar,
  cargando,
  error,
  textoEnviar = 'Guardar',
  peligro = false,
  children,
}: {
  abierto: boolean
  onCerrar: () => void
  titulo: ReactNode
  descripcion?: ReactNode
  onEnviar: () => void
  cargando?: boolean
  error?: string | null
  textoEnviar?: string
  peligro?: boolean
  children?: ReactNode
}) {
  const id = useId()
  const enviar = (e: FormEvent) => {
    e.preventDefault()
    onEnviar()
  }
  return (
    <Dialogo
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={titulo}
      descripcion={descripcion}
      acciones={
        <>
          <Boton variante="secundario" onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" form={id} variante={peligro ? 'peligro' : 'primario'} cargando={cargando}>
            {textoEnviar}
          </Boton>
        </>
      }
    >
      <form id={id} noValidate onSubmit={enviar} className="flex max-h-[65dvh] flex-col gap-4 overflow-y-auto px-0.5 pb-1">
        {error && <Aviso tono="peligro">{error}</Aviso>}
        {children}
      </form>
    </Dialogo>
  )
}
