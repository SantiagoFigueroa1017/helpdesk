import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Trash2, UserCheck, UserCog } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'

import { api } from '@/api/cliente'
import { ErrorApi, mensajeDeError } from '@/api/errores'
import { BuscadorUsuario, type UsuarioElegido } from '@/components/BuscadorUsuario'
import {
  AreaTexto,
  Aviso,
  Boton,
  CampoTexto,
  Cargando,
  Confirmar,
  DialogoFormulario,
  Insignia,
  Selector,
  Tarjeta,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import {
  COLOR_MODULO,
  ESTADO_TICKET,
  ESTADOS_TICKET,
  ETIQUETA_MODULO,
  ETIQUETA_TIPO_TICKET,
  MOTIVO_SIN_SOPORTE,
  formato,
  opcionesModulo,
  type EstadoTicket,
} from '@/lib/dominio'
import { useFormulario } from '@/lib/formulario'

import { CamposDetalle } from './CamposDetalle'
import { Comentarios } from './Comentarios'
import { detalleParaEnviar, detalleParaFormulario, useCamposModulo, type Ticket } from './tipos'

/**
 * Detalle de un ticket (HU22 a HU24): datos, campos del módulo, historial de estados y comentarios.
 * Las acciones aparecen según los permisos y el estado; el backend vuelve a revisar todo.
 */
export function PaginaTicket() {
  const { id = '' } = useParams()
  const { usuario, puede } = usePermisos()
  const cliente = useQueryClient()
  const navegar = useNavigate()
  const campos = useCamposModulo()
  const [error, setError] = useState<unknown>(null)
  const [dialogo, setDialogo] = useState<'editar' | 'asignar' | 'eliminar' | null>(null)
  const [nuevoEstado, setNuevoEstado] = useState('')

  const q = useQuery({ queryKey: ['tickets', 'detalle', id], queryFn: ({ signal }) => api<Ticket>(`/api/tickets/${id}`, { signal }) })

  const actualizado = (t: Ticket) => {
    cliente.setQueryData(['tickets', 'detalle', id], t)
    void cliente.invalidateQueries({ queryKey: ['tickets', 'lista'] })
    setError(null)
    setDialogo(null)
  }

  const cambiarEstado = useMutation({
    mutationFn: (estado: string) => api<Ticket>(`/api/tickets/${id}/estado`, { metodo: 'PUT', cuerpo: { estado } }),
    onSuccess: (t) => {
      setNuevoEstado('')
      actualizado(t)
    },
    onError: setError,
  })
  const asignar = useMutation({
    mutationFn: (agenteId: string | null) => api<Ticket>(`/api/tickets/${id}/asignacion`, { metodo: 'PUT', cuerpo: { agenteId } }),
    onSuccess: actualizado,
    onError: (e) => {
      setError(e)
      setDialogo(null)
    },
  })
  const eliminar = useMutation({
    mutationFn: () => api<{ comentariosEliminados: number; articulosDesvinculados: number }>(`/api/tickets/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['tickets'] })
      navegar('/tickets', { replace: true })
    },
  })

  if (q.isPending) return <Cargando texto="Cargando ticket…" />
  if (q.isError) {
    return (
      <div className="flex flex-col gap-4">
        <Volver />
        <Aviso tono="peligro">
          {q.error instanceof ErrorApi && q.error.codigo === 'RECURSO_NO_ENCONTRADO' ? 'Este ticket no existe o no tienes acceso a él.' : mensajeDeError(q.error)}
        </Aviso>
      </div>
    )
  }

  const t = q.data
  const yo = usuario?.id
  const abierto = t.estado === 'ABIERTO' || t.estado === 'EN_PROCESO'
  const puedeEditar =
    puede('TICKETS_EDITAR') || (puede('TICKETS_EDITAR_PROPIOS') && t.solicitanteId === yo && t.estado === 'ABIERTO' && !t.agenteId)
  const puedeEstado = t.agenteId === yo || puede('TICKETS_ASIGNAR_OTROS')
  const puedeAsignarme = puede('TICKETS_ASIGNAR') && !puede('TICKETS_ASIGNAR_OTROS') && !t.agenteId && abierto
  const puedeReasignar = puede('TICKETS_ASIGNAR_OTROS') && abierto
  const camposModulo = campos.data?.find((c) => c.modulo === t.modulo)?.campos ?? []
  const etiquetaCampo = (n: string) => camposModulo.find((c) => c.nombre === n)
  const conflicto = error instanceof ErrorApi && error.codigo === 'CONFLICTO'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Volver />
        <div className="mt-2 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight break-words">{t.titulo}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <Insignia tono={ESTADO_TICKET[t.estado].tono}>{ESTADO_TICKET[t.estado].etiqueta}</Insignia>
              <Insignia tono="primario" punto={COLOR_MODULO[t.modulo]}>{ETIQUETA_MODULO[t.modulo]}</Insignia>
              <Insignia>{ETIQUETA_TIPO_TICKET[t.tipo]}</Insignia>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {puedeEditar && (
              <Boton variante="secundario" icono={<Pencil aria-hidden className="size-4" />} onClick={() => setDialogo('editar')}>Editar</Boton>
            )}
            {puedeAsignarme && (
              <Boton icono={<UserCheck aria-hidden className="size-4" />} cargando={asignar.isPending} onClick={() => yo && asignar.mutate(yo)}>
                Asignarme
              </Boton>
            )}
            {puedeReasignar && (
              <Boton variante="secundario" icono={<UserCog aria-hidden className="size-4" />} onClick={() => setDialogo('asignar')}>
                {t.agenteId ? 'Reasignar' : 'Asignar'}
              </Boton>
            )}
            {puede('TICKETS_ELIMINAR') && (
              <Boton variante="peligro" icono={<Trash2 aria-hidden className="size-4" />} onClick={() => setDialogo('eliminar')}>Eliminar</Boton>
            )}
          </div>
        </div>
      </div>

      {error != null && (
        <Aviso
          tono={conflicto ? 'aviso' : 'peligro'}
          acciones={
            conflicto && (
              <Boton tamano="sm" variante="secundario" onClick={() => { setError(null); void q.refetch() }}>
                Recargar
              </Boton>
            )
          }
        >
          {error instanceof ErrorApi && error.codigo === 'SOPORTE_BLOQUEADO'
            ? `No se puede: ${MOTIVO_SIN_SOPORTE[error.motivo ?? ''] ?? 'la membresía no está vigente'}.`
            : conflicto
              ? 'Otra persona cambió este ticket mientras lo veías. Recarga para ver la versión actual.'
              : mensajeDeError(error)}
        </Aviso>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="flex min-w-0 flex-col gap-6">
          <Tarjeta titulo="Descripción">
            <p className="text-sm leading-relaxed whitespace-pre-wrap">{t.descripcion}</p>
            {t.detalle && Object.keys(t.detalle).length > 0 && (
              <dl className="mt-5 grid gap-x-6 gap-y-3 border-t border-borde pt-4 sm:grid-cols-2">
                {Object.entries(t.detalle).map(([k, v]) => {
                  const c = etiquetaCampo(k)
                  const texto = c?.tipo === 'OPCION' ? (c.opciones?.find((o) => o.valor === v)?.etiqueta ?? String(v))
                    : c?.tipo === 'DINERO' ? formato.pesos(v) : String(v)
                  return (
                    <div key={k} className={c?.tipo === 'TEXTO_LARGO' ? 'sm:col-span-2' : undefined}>
                      <dt className="text-xs text-texto-suave">{c?.etiqueta ?? k}</dt>
                      <dd className="text-sm break-words whitespace-pre-wrap">{texto}</dd>
                    </div>
                  )
                })}
              </dl>
            )}
          </Tarjeta>
          <Comentarios ticketId={t.id} abierto={abierto} />
        </div>

        <div className="flex flex-col gap-6">
          <Tarjeta titulo="Datos">
            <dl className="flex flex-col gap-3 text-sm">
              <Dato titulo="Empresa">{t.empresaNombre}</Dato>
              <Dato titulo="Solicitante">{t.solicitanteNombre}</Dato>
              {t.creadorId !== t.solicitanteId && <Dato titulo="Creado por">{t.creadorNombre}</Dato>}
              <Dato titulo="Agente">{t.agenteNombre ?? <span className="italic text-texto-suave">Sin asignar</span>}</Dato>
              <Dato titulo="Creado">{formato.fechaHora(t.fechaCreacion)}</Dato>
              <Dato titulo="Respuesta antes de">{formato.fechaHora(t.fechaLimiteRespuesta)}</Dato>
              {t.fechaPrimeraAsignacion && <Dato titulo="Primera asignación">{formato.fechaHora(t.fechaPrimeraAsignacion)}</Dato>}
              {t.fechaResolucion && <Dato titulo="Resuelto">{formato.fechaHora(t.fechaResolucion)}</Dato>}
            </dl>
          </Tarjeta>

          {puedeEstado && (
            <Tarjeta titulo="Cambiar estado">
              <form
                className="flex flex-col gap-3"
                onSubmit={(e) => {
                  e.preventDefault()
                  if (nuevoEstado) cambiarEstado.mutate(nuevoEstado)
                }}
              >
                <Selector
                  etiqueta="Nuevo estado"
                  vacio="Selecciona"
                  value={nuevoEstado}
                  onChange={(e) => setNuevoEstado(e.target.value)}
                  opciones={ESTADOS_TICKET.filter((e) => e !== t.estado).map((e) => ({ valor: e, etiqueta: ESTADO_TICKET[e].etiqueta }))}
                  ayuda={!t.agenteId ? 'En proceso y Resuelto necesitan un agente asignado.' : undefined}
                />
                <Boton type="submit" variante="secundario" disabled={!nuevoEstado} cargando={cambiarEstado.isPending}>Aplicar</Boton>
              </form>
            </Tarjeta>
          )}

          <Tarjeta titulo="Historial">
            <ol className="relative flex flex-col gap-4 border-l border-borde pl-4">
              {[...t.historialEstados].reverse().map((h, i) => (
                <li key={`${h.fecha}-${i}`} className="relative">
                  <span aria-hidden className="absolute top-1.5 -left-[1.3rem] size-2.5 rounded-full border-2 border-superficie bg-primario" />
                  <Insignia tono={ESTADO_TICKET[h.estado as EstadoTicket].tono}>{ESTADO_TICKET[h.estado as EstadoTicket].etiqueta}</Insignia>
                  <p className="mt-1 text-xs text-texto-suave">{formato.fechaHora(h.fecha)} · {h.usuarioNombre}</p>
                </li>
              ))}
            </ol>
          </Tarjeta>
        </div>
      </div>

      {dialogo === 'editar' && (
        <DialogoEditar ticket={t} total={puede('TICKETS_EDITAR')} onCerrar={() => setDialogo(null)} onGuardado={actualizado} />
      )}
      {dialogo === 'asignar' && (
        <DialogoAsignar
          actual={t.agenteNombre}
          enProceso={t.estado !== 'ABIERTO'}
          cargando={asignar.isPending}
          onCerrar={() => setDialogo(null)}
          onAsignar={(agente) => asignar.mutate(agente)}
        />
      )}
      <Confirmar
        abierto={dialogo === 'eliminar'}
        onCerrar={() => setDialogo(null)}
        onConfirmar={() => eliminar.mutate(undefined)}
        cargando={eliminar.isPending}
        error={eliminar.error}
        titulo="¿Eliminar el ticket?"
      >
        Se borran también sus comentarios y se quita de los artículos que lo citan. No se puede deshacer.
      </Confirmar>
    </div>
  )
}

function Volver() {
  return (
    <Link to="/tickets" className="inline-flex items-center gap-1 text-sm text-texto-suave hover:text-texto">
      <ArrowLeft aria-hidden className="size-4" /> Tickets
    </Link>
  )
}

function Dato({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-texto-suave">{titulo}</dt>
      <dd className="text-right font-medium">{children}</dd>
    </div>
  )
}

/** Editar (HU23): el cliente cambia título, descripción y campos; el agente además módulo y tipo. */
function DialogoEditar({ ticket, total, onCerrar, onGuardado }: { ticket: Ticket; total: boolean; onCerrar: () => void; onGuardado: (t: Ticket) => void }) {
  const campos = useCamposModulo()
  const form = useFormulario({ modulo: ticket.modulo as string, tipo: ticket.tipo as string, titulo: ticket.titulo, descripcion: ticket.descripcion, detalle: '' })
  const [detalle, setDetalle] = useState(() => detalleParaFormulario(ticket.detalle))
  const camposModulo = campos.data?.find((c) => c.modulo === form.valores.modulo)?.campos ?? []

  const guardar = useMutation({
    mutationFn: () =>
      api<Ticket>(`/api/tickets/${ticket.id}`, {
        metodo: 'PUT',
        cuerpo: {
          modulo: form.valores.modulo,
          tipo: form.valores.tipo,
          titulo: form.valores.titulo,
          descripcion: form.valores.descripcion,
          detalle: form.valores.tipo === 'INCIDENTE' ? detalleParaEnviar(camposModulo, detalle) : null,
        },
      }),
    onSuccess: onGuardado,
    onError: form.fallo,
  })

  return (
    <DialogoFormulario
      abierto
      onCerrar={onCerrar}
      titulo="Editar ticket"
      onEnviar={() => {
        form.limpiar()
        guardar.mutate(undefined)
      }}
      cargando={guardar.isPending}
      error={form.general}
    >
      {total && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Selector
            {...form.campo('modulo')}
            etiqueta="Módulo"
            opciones={opcionesModulo()}
            ayuda="Debe estar contratado por la empresa."
            onChange={(e) => {
              form.poner('modulo', e.target.value)
              setDetalle({})
            }}
          />
          <Selector {...form.campo('tipo')} etiqueta="Tipo" opciones={[{ valor: 'INCIDENTE', etiqueta: 'Incidente' }, { valor: 'SUGERENCIA', etiqueta: 'Sugerencia' }]} />
        </div>
      )}
      <CampoTexto {...form.campo('titulo')} etiqueta="Título" obligatorio maxLength={150} />
      <AreaTexto {...form.campo('descripcion')} etiqueta="Descripción" obligatorio rows={5} />
      {form.valores.tipo === 'INCIDENTE' && camposModulo.length > 0 && (
        <CamposDetalle campos={camposModulo} valores={detalle} errores={form.errores} onCambiar={(n, v) => setDetalle((d) => ({ ...d, [n]: v }))} />
      )}
    </DialogoFormulario>
  )
}

/** Asignar o reasignar (TICKETS_ASIGNAR_OTROS): a otro agente o dejarlo sin agente. */
function DialogoAsignar({
  actual,
  enProceso,
  cargando,
  onCerrar,
  onAsignar,
}: {
  actual: string | null
  enProceso: boolean
  cargando: boolean
  onCerrar: () => void
  onAsignar: (agenteId: string | null) => void
}) {
  const [agente, setAgente] = useState<UsuarioElegido | null>(null)
  const [quitar, setQuitar] = useState(false)
  return (
    <DialogoFormulario
      abierto
      onCerrar={onCerrar}
      titulo="Asignar agente"
      descripcion={actual ? `Agente actual: ${actual}.` : 'El ticket no tiene agente.'}
      textoEnviar={quitar ? 'Quitar agente' : 'Asignar'}
      cargando={cargando}
      onEnviar={() => (quitar ? onAsignar(null) : agente && onAsignar(agente.id))}
    >
      {!quitar && (
        <BuscadorUsuario etiqueta="Agente" tipo="personal" valor={agente} onCambiar={setAgente} ayuda="Personal de soporte activo con permiso para atender tickets." />
      )}
      {actual && (
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-primario" checked={quitar} disabled={enProceso} onChange={(e) => setQuitar(e.target.checked)} />
          Dejar el ticket sin agente{enProceso && ' (primero pásalo a Abierto)'}
        </label>
      )}
    </DialogoFormulario>
  )
}
