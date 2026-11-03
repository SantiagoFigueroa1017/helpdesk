import { useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

/**
 * Diálogo modal con el elemento <dialog> del navegador: atrapa el foco, cierra con Escape y
 * devuelve el foco al botón que lo abrió, sin librerías. Se controla con "abierto" y "onCerrar".
 */
export function Dialogo({
  abierto,
  onCerrar,
  titulo,
  descripcion,
  acciones,
  children,
}: {
  abierto: boolean
  onCerrar: () => void
  titulo: ReactNode
  descripcion?: ReactNode
  acciones?: ReactNode
  children?: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const tituloId = useId()

  useEffect(() => {
    const dialogo = ref.current
    if (!dialogo) return
    if (abierto && !dialogo.open) dialogo.showModal()
    if (!abierto && dialogo.open) dialogo.close()
  }, [abierto])

  return (
    <dialog
      ref={ref}
      aria-labelledby={tituloId}
      onClose={onCerrar}
      onClick={(e) => {
        // Clic en el fondo oscuro (fuera del contenido) cierra el diálogo.
        if (e.target === ref.current) onCerrar()
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-tarjeta border border-borde bg-superficie p-0 text-texto shadow-xl backdrop:bg-black/45"
    >
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id={tituloId} className="text-lg font-semibold">
              {titulo}
            </h2>
            {descripcion && <p className="mt-1 text-sm text-texto-suave">{descripcion}</p>}
          </div>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="cursor-pointer rounded-control p-1 text-texto-suave hover:bg-superficie-2 hover:text-texto"
          >
            <X aria-hidden className="size-5" />
          </button>
        </div>
        {children && <div className="mt-4">{children}</div>}
      </div>
      {acciones && (
        <div className="flex flex-col-reverse gap-2 border-t border-borde bg-superficie-2/50 px-5 py-3 sm:flex-row sm:justify-end">
          {acciones}
        </div>
      )}
    </dialog>
  )
}
