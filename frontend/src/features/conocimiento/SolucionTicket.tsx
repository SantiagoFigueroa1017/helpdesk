import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
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
import type { Articulo } from './tipos'

type TicketBase = { id: string; titulo: string; descripcion: string; modulo: ModuloErp; estado: string }

/**
 * Base de conocimientos dentro del ticket (contexto 9.2). El agente busca una solución documentada
 * mientras atiende el ticket y la vincula (POST /api/articulos/{id}/tickets/{ticketId}); si el
 * problema es nuevo, lo documenta en un artículo que nace vinculado al ticket (HU29 con ticketIds).
 * El cliente ve solo los artículos públicos y puede intentar resolverlo por su cuenta.
 */
export function SolucionTicket({ ticket }: { ticket: TicketBase }) {
  const { puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const [modo, setModo] = useState<ModoBusqueda>('texto')
  const [texto, setTexto] = useState(ticket.titulo)
  const [soloModulo, setSoloModulo] = useState(true)
  const [nuevo, setNuevo] = useState(false)
  const [creado, setCreado] = useState<Articulo | null>(null)
  // Artículos vinculados o desvinculados en esta visita (la búsqueda no trae los tickets de cada artículo).
  const [vinculos, setVinculos] = useState<Record<string, boolean>>({})

  const gestiona = esPersonal && puede('ARTICULOS_EDITAR') && puede('TICKETS_VER')
  const { listo, palabras, buscado, consulta } = useBusquedaArticulos(modo, texto, soloModulo ? ticket.modulo : '', 5)

  const vincular = useMutation({
    mutationFn: (v: { articuloId: string; quitar: boolean }) =>
      api<Articulo>(`/api/articulos/${v.articuloId}/tickets/${ticket.id}`, { metodo: v.quitar ? 'DELETE' : 'POST' }),
    onSuccess: (a, v) => {
      setVinculos((m) => ({ ...m, [a.id]: !v.quitar }))
      cliente.setQueryData(['articulos', 'detalle', a.id], a)
      void cliente.invalidateQueries({ queryKey: ['articulos', 'lista'] })
    },
  })

  if (!puede('ARTICULOS_VER')) return null

  return (
    <Tarjeta
      titulo={esPersonal ? 'Solución en la base de conocimientos' : '¿Ya existe una solución?'}
      descripcion={
        esPersonal
          ? 'Busca un artículo que resuelva este ticket y vincúlalo. Si el problema es nuevo, documéntalo.'
          : 'Puede que tu problema ya tenga una solución documentada.'
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
            ticket.{ticket.estado !== 'RESUELTO' && ticket.estado !== 'CERRADO' && ' Ya puedes marcar el ticket como Resuelto.'}
          </Aviso>
        )}

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
                  const vinculado = vinculos[r.id] === true
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
            {Object.values(vinculos).some(Boolean) && ticket.estado !== 'RESUELTO' && ticket.estado !== 'CERRADO' && (
              <p className="flex items-center gap-1.5 text-sm text-exito-texto">
                <Check aria-hidden className="size-4" /> Solución vinculada. Ya puedes marcar el ticket como Resuelto.
              </p>
            )}
            {!esPersonal && <p className="text-xs text-texto-suave">Si un artículo resolvió tu problema, cuéntalo en los comentarios.</p>}
          </div>
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
            setVinculos((m) => ({ ...m, [a.id]: true }))
          }}
        />
      )}
    </Tarjeta>
  )
}
