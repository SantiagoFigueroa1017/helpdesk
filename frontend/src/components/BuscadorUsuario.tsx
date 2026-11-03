import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X } from 'lucide-react'

import { api, type Pagina } from '@/api/cliente'
import type { Usuario } from '@/api/catalogos'
import { CampoTexto } from '@/components/ui'
import { useRetardo } from '@/lib/useRetardo'

export type UsuarioElegido = { id: string; nombre: string; empresaId: string | null }

/**
 * Elegir un usuario escribiendo el comienzo de su nombre (GET /api/usuarios?texto=, USUARIOS_VER).
 * "tipo" deja solo al personal (agentes, instructores) o solo a usuarios de empresa (solicitantes).
 */
export function BuscadorUsuario({
  etiqueta,
  valor,
  onCambiar,
  tipo,
  empresaId,
  error,
  ayuda,
  obligatorio,
}: {
  etiqueta: string
  valor: UsuarioElegido | null
  onCambiar: (u: UsuarioElegido | null) => void
  tipo?: 'personal' | 'empresa'
  empresaId?: string
  error?: string
  ayuda?: string
  obligatorio?: boolean
}) {
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim(), 300)
  const q = useQuery({
    queryKey: ['usuarios', 'buscador', buscado, empresaId ?? null],
    queryFn: ({ signal }) =>
      api<Pagina<Usuario>>('/api/usuarios', { params: { texto: buscado, empresaId, activo: true, tamano: 25 }, signal }),
    enabled: buscado.length >= 2 && valor === null,
  })
  const resultados = (q.data?.contenido ?? []).filter((u) =>
    tipo === 'personal' ? u.empresaId === null : tipo === 'empresa' ? u.empresaId !== null : true,
  )

  if (valor) {
    return (
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium">{etiqueta}</span>
        <div className="flex h-10 items-center justify-between rounded-control border border-borde bg-superficie-2 px-3 text-sm">
          <span className="truncate">{valor.nombre}</span>
          <button
            type="button"
            onClick={() => onCambiar(null)}
            aria-label={`Quitar a ${valor.nombre}`}
            className="cursor-pointer rounded p-1 text-texto-suave hover:text-texto"
          >
            <X aria-hidden className="size-4" />
          </button>
        </div>
        {error && <p className="text-xs font-medium text-peligro-texto">{error}</p>}
      </div>
    )
  }

  return (
    <div className="relative">
      <CampoTexto
        etiqueta={etiqueta}
        obligatorio={obligatorio}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
        placeholder="Escribe al menos 2 letras del nombre"
        autoComplete="off"
        error={error}
        ayuda={ayuda}
      />
      {buscado.length >= 2 && (
        <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-control border border-borde bg-superficie py-1 shadow-lg">
          {q.isPending && <li className="px-3 py-2 text-sm text-texto-suave">Buscando…</li>}
          {!q.isPending && resultados.length === 0 && <li className="px-3 py-2 text-sm text-texto-suave">Sin coincidencias.</li>}
          {resultados.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                className="flex w-full cursor-pointer flex-col px-3 py-2 text-left text-sm hover:bg-superficie-2"
                onClick={() => {
                  onCambiar({ id: u.id, nombre: u.nombre, empresaId: u.empresaId })
                  setTexto('')
                }}
              >
                <span className="font-medium">{u.nombre}</span>
                <span className="text-xs text-texto-suave">{u.email}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
