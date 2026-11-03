import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2 } from 'lucide-react'

import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { AreaTexto, Aviso, Boton, Cargando, Confirmar, Paginador, Tarjeta } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { formato } from '@/lib/dominio'

import type { Comentario } from './tipos'

/**
 * Comentarios del ticket (HU22 y HU23), del más antiguo al más nuevo. Se escribe solo con el ticket
 * abierto; el autor edita los suyos y los borra (o quien tiene COMENTARIOS_ELIMINAR, cualquiera).
 */
export function Comentarios({ ticketId, abierto }: { ticketId: string; abierto: boolean }) {
  const { usuario, puede } = usePermisos()
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [texto, setTexto] = useState('')
  const [editando, setEditando] = useState<{ id: string; texto: string } | null>(null)
  const [borrar, setBorrar] = useState<Comentario | null>(null)
  const clave = ['comentarios', ticketId]

  const q = useQuery({
    queryKey: [...clave, pagina],
    queryFn: ({ signal }) =>
      api<Pagina<Comentario>>(`/api/tickets/${ticketId}/comentarios`, { params: { pagina, tamano: 20, orden: 'fechaCreacion,asc' }, signal }),
    placeholderData: (anterior) => anterior,
  })
  const refrescar = () => cliente.invalidateQueries({ queryKey: clave })

  const crear = useMutation({
    mutationFn: () => api<Comentario>(`/api/tickets/${ticketId}/comentarios`, { metodo: 'POST', cuerpo: { texto } }),
    onSuccess: async () => {
      setTexto('')
      await refrescar()
      // El nuevo queda al final: ir a la última página.
      const total = (q.data?.totalElementos ?? 0) + 1
      setPagina(Math.max(0, Math.ceil(total / 20) - 1))
    },
  })
  const editar = useMutation({
    mutationFn: (c: { id: string; texto: string }) => api<Comentario>(`/api/comentarios/${c.id}`, { metodo: 'PUT', cuerpo: { texto: c.texto } }),
    onSuccess: async () => {
      setEditando(null)
      await refrescar()
    },
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/comentarios/${id}`, { metodo: 'DELETE' }),
    onSuccess: async () => {
      setBorrar(null)
      await refrescar()
    },
  })

  return (
    <Tarjeta titulo="Comentarios" sinRelleno>
      {q.isPending ? (
        <Cargando />
      ) : q.isError ? (
        <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div>
      ) : (
        <>
          {q.data.contenido.length === 0 ? (
            <p className="px-5 py-6 text-sm text-texto-suave">Aún no hay comentarios.</p>
          ) : (
            <ul className="divide-y divide-borde">
              {q.data.contenido.map((c) => {
                const mio = c.autorId === usuario?.id
                return (
                  <li key={c.id} className="px-4 py-4 sm:px-5">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <p className="text-sm font-semibold">
                        {c.autorNombre}
                        <span className="ml-2 font-normal text-texto-suave">
                          {formato.fechaHora(c.fechaCreacion)}
                          {c.editado && ' · editado'}
                        </span>
                      </p>
                      <div className="flex gap-1">
                        {mio && abierto && editando?.id !== c.id && (
                          <Boton variante="fantasma" tamano="sm" aria-label="Editar comentario" onClick={() => setEditando({ id: c.id, texto: c.texto })}>
                            <Pencil aria-hidden className="size-4" />
                          </Boton>
                        )}
                        {(mio || puede('COMENTARIOS_ELIMINAR')) && (
                          <Boton variante="fantasma" tamano="sm" aria-label="Eliminar comentario" onClick={() => setBorrar(c)}>
                            <Trash2 aria-hidden className="size-4" />
                          </Boton>
                        )}
                      </div>
                    </div>
                    {editando?.id === c.id ? (
                      <form
                        className="mt-2 flex flex-col gap-2"
                        onSubmit={(e) => {
                          e.preventDefault()
                          editar.mutate(editando)
                        }}
                      >
                        <AreaTexto etiqueta="Editar comentario" rows={3} value={editando.texto} onChange={(e) => setEditando({ id: c.id, texto: e.target.value })}
                          error={editar.isError ? mensajeDeError(editar.error) : undefined} />
                        <div className="flex justify-end gap-2">
                          <Boton tamano="sm" variante="secundario" onClick={() => setEditando(null)}>Cancelar</Boton>
                          <Boton tamano="sm" type="submit" cargando={editar.isPending}>Guardar</Boton>
                        </div>
                      </form>
                    ) : (
                      <p className="mt-1 text-sm leading-relaxed whitespace-pre-wrap">{c.texto}</p>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
          <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
        </>
      )}

      {puede('COMENTARIOS_CREAR') && (
        <div className="border-t border-borde p-4 sm:p-5">
          {abierto ? (
            <form
              className="flex flex-col gap-3"
              onSubmit={(e) => {
                e.preventDefault()
                if (texto.trim()) crear.mutate(undefined)
              }}
            >
              <AreaTexto etiqueta="Nuevo comentario" rows={3} value={texto} onChange={(e) => setTexto(e.target.value)}
                error={crear.isError ? mensajeDeError(crear.error) : undefined} />
              <div className="flex justify-end">
                <Boton type="submit" disabled={!texto.trim()} cargando={crear.isPending}>Comentar</Boton>
              </div>
            </form>
          ) : (
            <p className="text-sm text-texto-suave">El ticket está resuelto o cerrado: ya no recibe comentarios.</p>
          )}
        </div>
      )}

      <Confirmar
        abierto={borrar !== null}
        onCerrar={() => setBorrar(null)}
        onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending}
        error={eliminar.error}
        titulo="¿Eliminar el comentario?"
      >
        No se puede deshacer.
      </Confirmar>
    </Tarjeta>
  )
}
