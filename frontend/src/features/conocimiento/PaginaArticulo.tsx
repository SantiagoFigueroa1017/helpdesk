import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Link2, Pencil, Trash2, X } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'

import { api } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, Boton, CampoTexto, Cargando, Confirmar, Insignia, Tarjeta } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { COLOR_MODULO, ETIQUETA_MODULO, formato } from '@/lib/dominio'

import { FormularioArticulo } from './FormularioArticulo'
import type { Articulo } from './tipos'

/** Un artículo con su contenido y los tickets que resolvió (HU16 y HU17). */
export function PaginaArticulo() {
  const { id = '' } = useParams()
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const navegar = useNavigate()
  const [editar, setEditar] = useState(false)
  const [borrar, setBorrar] = useState(false)
  const [ticketId, setTicketId] = useState('')
  const clave = ['articulos', 'detalle', id]

  const q = useQuery({ queryKey: clave, queryFn: ({ signal }) => api<Articulo>(`/api/articulos/${id}`, { signal }) })
  const guardado = (a: Articulo) => {
    cliente.setQueryData(clave, a)
    void cliente.invalidateQueries({ queryKey: ['articulos', 'lista'] })
  }

  const vincular = useMutation({
    mutationFn: (v: { ticket: string; quitar: boolean }) =>
      api<Articulo>(`/api/articulos/${id}/tickets/${encodeURIComponent(v.ticket)}`, { metodo: v.quitar ? 'DELETE' : 'POST' }),
    onSuccess: (a) => {
      setTicketId('')
      guardado(a)
    },
  })
  const eliminar = useMutation({
    mutationFn: () => api<void>(`/api/articulos/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['articulos'] })
      navegar('/conocimiento', { replace: true })
    },
  })

  const volver = (
    <Link to="/conocimiento" className="inline-flex items-center gap-1 text-sm text-texto-suave hover:text-texto">
      <ArrowLeft aria-hidden className="size-4" /> Base de conocimientos
    </Link>
  )
  if (q.isPending) return <Cargando />
  if (q.isError) return <div className="flex flex-col gap-4">{volver}<Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div>

  const a = q.data
  return (
    <div className="flex flex-col gap-6">
      <div>
        {volver}
        <div className="mt-2 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight">{a.titulo}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <Insignia tono="primario" punto={COLOR_MODULO[a.modulo]}>{ETIQUETA_MODULO[a.modulo]}</Insignia>
              {a.visibilidad === 'INTERNO' && <Insignia tono="aviso">Interno</Insignia>}
              {a.etiquetas.map((e) => <Insignia key={e}>#{e}</Insignia>)}
            </div>
          </div>
          <div className="flex gap-2">
            {puede('ARTICULOS_EDITAR') && (
              <Boton variante="secundario" icono={<Pencil aria-hidden className="size-4" />} onClick={() => setEditar(true)}>Editar</Boton>
            )}
            {puede('ARTICULOS_ELIMINAR') && (
              <Boton variante="peligro" icono={<Trash2 aria-hidden className="size-4" />} onClick={() => setBorrar(true)}>Eliminar</Boton>
            )}
          </div>
        </div>
      </div>

      {a.vectorPendiente && puede('ARTICULOS_EDITAR') && (
        <Aviso tono="aviso">Este artículo aún no tiene vector: no aparece en la búsqueda por significado hasta que el servicio de embeddings responda.</Aviso>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Tarjeta>
          <div className="text-sm leading-relaxed whitespace-pre-wrap">{a.contenido}</div>
          <p className="mt-6 border-t border-borde pt-3 text-xs text-texto-suave">
            Escrito por {a.autorNombre} · actualizado el {formato.fecha(a.fechaActualizacion)}
          </p>
        </Tarjeta>

        <Tarjeta titulo="Tickets resueltos" descripcion={`${a.ticketsVinculados} ${a.ticketsVinculados === 1 ? 'ticket' : 'tickets'} con esta solución.`}>
          {puede('TICKETS_VER') && a.ticketIds.length > 0 && (
            <ul className="flex flex-col gap-1.5 text-sm">
              {a.ticketIds.map((t) => (
                <li key={t} className="flex items-center justify-between gap-2">
                  <Link to={`/tickets/${t}`} className="truncate font-mono text-xs text-acento hover:underline">{t}</Link>
                  {puede('ARTICULOS_EDITAR') && (
                    <button
                      type="button"
                      aria-label={`Desvincular ticket ${t}`}
                      className="cursor-pointer rounded p-1 text-texto-suave hover:text-peligro-texto"
                      onClick={() => vincular.mutate({ ticket: t, quitar: true })}
                    >
                      <X aria-hidden className="size-4" />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {puede('ARTICULOS_EDITAR') && (
            <form
              className="mt-4 flex flex-col gap-2"
              onSubmit={(e) => {
                e.preventDefault()
                if (ticketId.trim()) vincular.mutate({ ticket: ticketId.trim(), quitar: false })
              }}
            >
              <CampoTexto etiqueta="Vincular ticket" placeholder="Id del ticket" value={ticketId} onChange={(e) => setTicketId(e.target.value)}
                error={vincular.isError ? mensajeDeError(vincular.error) : undefined} ayuda="Copia el id desde la dirección del ticket." />
              <Boton type="submit" variante="secundario" tamano="sm" icono={<Link2 aria-hidden className="size-4" />} cargando={vincular.isPending}>
                Vincular
              </Boton>
            </form>
          )}
        </Tarjeta>
      </div>

      {editar && <FormularioArticulo articulo={a} onCerrar={() => setEditar(false)} onGuardado={(n) => { guardado(n); setEditar(false) }} />}
      <Confirmar abierto={borrar} onCerrar={() => setBorrar(false)} onConfirmar={() => eliminar.mutate(undefined)} cargando={eliminar.isPending}
        error={eliminar.error} titulo="¿Eliminar el artículo?">
        Los tickets vinculados no se borran. No se puede deshacer.
      </Confirmar>
    </div>
  )
}
