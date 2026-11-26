import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, FilePlus2, Link2, Unlink } from 'lucide-react'
import { Link } from 'react-router'

import { mensajeDeError } from '@/api/errores'
import { api } from '@/api/cliente'
import { Aviso, Boton, Cargando, Casilla, CampoTexto, Insignia, Tarjeta } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { ETIQUETA_MODULO, type ModuloErp } from '@/lib/dominio'

import { mensajeAviso, useBusquedaArticulos, type ModoBusqueda } from './busqueda'
import { FormularioArticulo } from './FormularioArticulo'
import { SelectorModo } from './SelectorModo'
import type { Articulo, ArticuloResumen } from './tipos'

type TicketBase = { id: string; titulo: string; descripcion: string; modulo: ModuloErp; estado: string }

/**
 * Base de conocimientos dentro del ticket (contexto 9.2). El agente busca una solución documentada
 * mientras atiende el ticket y la vincula (POST /api/articulos/{id}/tickets/{ticketId}); si el
 * problema es nuevo, lo documenta en un artículo que nace vinculado al ticket (HU29 con ticketIds).
 * El cliente ve solo los artículos públicos y puede intentar resolverlo por su cuenta.
 *
 * Arriba muestra la "Solución aplicada": los artículos ya vinculados al ticket
 * (GET /api/articulos/por-ticket/{ticketId}), así la trazabilidad se conserva al recargar.
 */
export function SolucionTicket({ ticket }: { ticket: TicketBase }) {
  const { puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const [modo, setModo] = useState<ModoBusqueda>('texto')
  const [texto, setTexto] = useState(ticket.titulo)
  const [soloModulo, setSoloModulo] = useState(true)
  const [nuevo, setNuevo] = useState(false)
  const [creado, setCreado] = useState<Articulo | null>(null)

  const gestiona = esPersonal && puede('ARTICULOS_EDITAR') && puede('TICKETS_VER')
  const { listo, palabras, buscado, consulta } = useBusquedaArticulos(modo, texto, soloModulo ? ticket.modulo : '', 5)
  const claveVinculados = ['articulos', 'por-ticket', ticket.id]
  const vinculados = useQuery({
    queryKey: claveVinculados,
    queryFn: ({ signal }) => api<ArticuloResumen[]>(`/api/articulos/por-ticket/${ticket.id}`, { signal }),
    enabled: puede('ARTICULOS_VER'),
  })
  const idsVinculados = new Set((vinculados.data ?? []).map((a) => a.id))
  const abierto = ticket.estado === 'ABIERTO' || ticket.estado === 'EN_PROCESO'

  const vincular = useMutation({
    mutationFn: (v: { articuloId: string; quitar: boolean }) =>
      api<Articulo>(`/api/articulos/${v.articuloId}/tickets/${ticket.id}`, { metodo: v.quitar ? 'DELETE' : 'POST' }),
    onSuccess: (a) => {
      cliente.setQueryData(['articulos', 'detalle', a.id], a)
      void cliente.invalidateQueries({ queryKey: claveVinculados })
      void cliente.invalidateQueries({ queryKey: ['articulos', 'lista'] })
    },
  })

  if (!puede('ARTICULOS_VER')) return null
  // Al cliente, con el ticket ya resuelto o cerrado, solo le sirve ver la solución aplicada.
  if (!gestiona && !abierto && idsVinculados.size === 0) return null

  return (
    <Tarjeta
      titulo={esPersonal ? 'Solución en la base de conocimientos' : abierto ? '¿Ya existe una solución?' : 'Solución de tu ticket'}
      descripcion={
        esPersonal
          ? 'Busca un artículo que resuelva este ticket y vincúlalo. Si el problema es nuevo, documéntalo.'
          : abierto
            ? 'Puede que tu problema ya tenga una solución documentada.'
            : 'El artículo con el que el agente resolvió tu caso.'
      }
      acciones={
        gestiona &&
        puede('ARTICULOS_CREAR') && (
          <Boton tamano="sm" variante="secundario" icono={<FilePlus2 aria-hidden className="size-4" />} onClick={() => setNuevo(true)}>
            Documentar solución
          </Boton>
        )
      }
    >
      <div className="flex flex-col gap-4">
        {creado && (
          <Aviso tono="exito" titulo="Solución documentada">
            Se creó <Link to={`/conocimiento/${creado.id}`} className="font-medium underline">{creado.titulo}</Link> vinculado a este
            ticket.
          </Aviso>
        )}

        {vinculados.data && vinculados.data.length > 0 && (
          <div className="rounded-control border border-exito-texto/30 bg-exito-fondo p-3">
            <p className="flex items-center gap-1.5 text-sm font-semibold text-exito-texto">
              <Check aria-hidden className="size-4" /> Solución aplicada
            </p>
            <ul className="mt-2 flex flex-col gap-1.5">
              {vinculados.data.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                  <span className="flex flex-wrap items-center gap-2">
                    <Link to={`/conocimiento/${a.id}`} className="font-medium text-texto hover:text-primario hover:underline">{a.titulo}</Link>
                    {a.visibilidad === 'INTERNO' && <Insignia tono="aviso">Interno</Insignia>}
                  </span>
                  {gestiona && (
                    <Boton
                      tamano="sm"
                      variante="fantasma"
                      cargando={vincular.isPending && vincular.variables?.articuloId === a.id}
                      icono={<Unlink aria-hidden className="size-4" />}
                      onClick={() => vincular.mutate({ articuloId: a.id, quitar: true })}
                    >
                      Quitar vínculo
                    </Boton>
                  )}
                </li>
              ))}
            </ul>
            {gestiona && abierto && <p className="mt-2 text-xs text-exito-texto">Ya puedes marcar el ticket como Resuelto.</p>}
          </div>
        )}

        {!gestiona && !abierto ? null : (
          <>
        <SelectorModo modo={modo} onCambiar={setModo} />
        <CampoTexto
          etiqueta={modo === 'texto' ? 'Buscar por palabras' : 'Describe el problema'}
          type="search"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          ayuda={modo === 'significado' ? 'Al menos 3 palabras. Encuentra artículos aunque usen otras palabras.' : undefined}
        />
        <Casilla etiqueta={`Solo artículos de ${ETIQUETA_MODULO[ticket.modulo]}`} checked={soloModulo} onChange={(e) => setSoloModulo(e.target.checked)} />

        {vincular.isError && <Aviso tono="peligro">{mensajeDeError(vincular.error)}</Aviso>}

        {buscado.length === 0 ? null : !listo ? (
          <p className="text-sm text-texto-suave">{modo === 'texto' ? 'Escribe al menos 2 letras.' : `Escribe al menos 3 palabras (llevas ${palabras}).`}</p>
        ) : consulta.isPending ? (
          <Cargando texto="Buscando…" />
        ) : consulta.isError ? (
          <Aviso tono="peligro">{mensajeDeError(consulta.error)}</Aviso>
        ) : (
          <div className="flex flex-col gap-3">
            {consulta.data.aviso && <Aviso tono="aviso">{mensajeAviso(consulta.data.aviso)}</Aviso>}
            {consulta.data.resultados.length === 0 ? (
              <p className="text-sm text-texto-suave">
                No hay artículos con esa búsqueda.{gestiona && puede('ARTICULOS_CREAR') && ' Si el problema es nuevo, usa "Documentar solución".'}
              </p>
            ) : (
              <ol className="flex flex-col divide-y divide-borde">
                {consulta.data.resultados.map((r) => {
                  const vinculado = idsVinculados.has(r.id)
                  const ocupado = vincular.isPending && vincular.variables?.articuloId === r.id
                  return (
                    <li key={r.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <Link to={`/conocimiento/${r.id}`} className="font-medium hover:text-primario hover:underline">{r.titulo}</Link>
                          {r.visibilidad === 'INTERNO' && <Insignia tono="aviso">Interno</Insignia>}
                          {vinculado && <Insignia tono="exito">Vinculado</Insignia>}
                        </div>
                        <p className="mt-1 line-clamp-2 text-sm text-texto-suave">{r.extracto}</p>
                      </div>
                      {gestiona && (
                        <Boton
                          tamano="sm"
                          variante={vinculado ? 'fantasma' : 'secundario'}
                          className="shrink-0"
                          cargando={ocupado}
                          icono={vinculado ? <Unlink aria-hidden className="size-4" /> : <Link2 aria-hidden className="size-4" />}
                          onClick={() => vincular.mutate({ articuloId: r.id, quitar: vinculado })}
                        >
                          {vinculado ? 'Quitar vínculo' : 'Usar como solución'}
                        </Boton>
                      )}
                    </li>
                  )
                })}
              </ol>
            )}
            {!esPersonal && <p className="text-xs text-texto-suave">Si un artículo resolvió tu problema, cuéntalo en los comentarios.</p>}
          </div>
        )}
          </>
        )}
      </div>

      {nuevo && (
        <FormularioArticulo
          inicial={{
            titulo: ticket.titulo,
            modulo: ticket.modulo,
            visibilidad: 'INTERNO',
            contenido: `Problema reportado:\n${ticket.descripcion}\n\nCausa:\n\nSolución:\n1. `,
            ticketIds: [ticket.id],
          }}
          onCerrar={() => setNuevo(false)}
          onGuardado={(a) => {
            setNuevo(false)
            setCreado(a)
            void cliente.invalidateQueries({ queryKey: claveVinculados })
          }}
        />
      )}
    </Tarjeta>
  )
}
