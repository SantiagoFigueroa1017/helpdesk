import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, Plus, Trash2, X } from 'lucide-react'

import { useMembresia } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { BuscadorUsuario, type UsuarioElegido } from '@/components/BuscadorUsuario'
import {
  AreaTexto, Aviso, Boton, CampoTexto, Cargando, Confirmar, DialogoFormulario, Encabezado, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import {
  COLOR_MODULO, ESTADO_SOLICITUD, ETIQUETA_MODULO, desdeFechaHoraLocal, formato, formatoDia, hoyLocal, opcionesModulo, type ModuloErp,
} from '@/lib/dominio'
import { nulo, numero, useFormulario } from '@/lib/formulario'

import type { Solicitud } from './tipos'

/**
 * Solicitudes de capacitación personalizada (HU33 v.4, tarea 2.8). El CLIENTE_ADMIN la pide para un módulo
 * contratado; el personal la programa (nace la capacitación) o la rechaza con un motivo.
 */
export function PaginaSolicitudes() {
  const { usuario, puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [estado, setEstado] = useState(esPersonal ? 'PENDIENTE' : '')
  const [dialogo, setDialogo] = useState<{ tipo: 'nueva' } | { tipo: 'programar' | 'rechazar' | 'eliminar'; s: Solicitud } | null>(null)
  const gestiona = puede('SOLICITUDES_GESTIONAR')

  const q = useQuery({
    queryKey: ['solicitudes', { pagina, estado }],
    queryFn: ({ signal }) => api<Pagina<Solicitud>>('/api/solicitudes-capacitacion', { params: { pagina, estado, orden: esPersonal ? 'fechaCreacion,asc' : 'fechaCreacion,desc' }, signal }),
    placeholderData: (anterior) => anterior,
  })
  const listo = () => { setDialogo(null); void cliente.invalidateQueries({ queryKey: ['solicitudes'] }) }
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/solicitudes-capacitacion/${id}`, { metodo: 'DELETE' }),
    onSuccess: listo,
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Solicitudes de capacitación" descripcion={gestiona ? 'Pedidos de capacitación personalizada de las empresas.' : 'Pide una capacitación para el equipo de tu empresa.'}
        acciones={puede('CAPACITACIONES_SOLICITAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setDialogo({ tipo: 'nueva' })}>Nueva solicitud</Boton>} />
      <div className="max-w-xs">
        <Selector etiqueta="Estado" vacio="Todas" value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0) }}
          opciones={Object.entries(ESTADO_SOLICITUD).map(([valor, e]) => ({ valor, etiqueta: e.etiqueta }))} />
      </div>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay solicitudes" /> : (
          <>
            <Tabla>
              <thead><tr>{esPersonal && <Th>Empresa</Th>}<Th>Módulo</Th><Th>Pedido</Th><Th>Estado</Th><Th>Solicitada</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((s) => (
                  <Tr key={s.id}>
                    {esPersonal && <Td className="font-medium">{s.empresaNombre}</Td>}
                    <Td><Insignia tono="primario" punto={COLOR_MODULO[s.modulo]}>{ETIQUETA_MODULO[s.modulo]}</Insignia></Td>
                    <Td className="max-w-md">
                      <p className="line-clamp-2">{s.descripcion}</p>
                      <p className="text-xs text-texto-suave">
                        {s.participantesEstimados} participantes{s.fechaPreferida && ` · prefiere el ${formatoDia(s.fechaPreferida)}`} · {s.solicitanteNombre}
                      </p>
                      {s.motivoRechazo && <p className="mt-1 text-xs text-peligro-texto">Rechazada: {s.motivoRechazo}</p>}
                    </Td>
                    <Td><Insignia tono={ESTADO_SOLICITUD[s.estado].tono}>{ESTADO_SOLICITUD[s.estado].etiqueta}</Insignia></Td>
                    <Td className="whitespace-nowrap text-texto-suave">{formato.fecha(s.fechaCreacion)}</Td>
                    <Td className="text-right whitespace-nowrap">
                      {gestiona && s.estado === 'PENDIENTE' && (
                        <>
                          <Boton tamano="sm" variante="secundario" icono={<CalendarPlus aria-hidden className="size-4" />} onClick={() => setDialogo({ tipo: 'programar', s })}>Programar</Boton>
                          <Boton tamano="sm" variante="fantasma" icono={<X aria-hidden className="size-4" />} onClick={() => setDialogo({ tipo: 'rechazar', s })}>Rechazar</Boton>
                        </>
                      )}
                      {s.estado !== 'PROGRAMADA' && (gestiona || s.solicitanteId === usuario?.id) && (
                        <Boton tamano="sm" variante="fantasma" aria-label="Eliminar solicitud" onClick={() => { eliminar.reset(); setDialogo({ tipo: 'eliminar', s }) }}><Trash2 aria-hidden className="size-4" /></Boton>
                      )}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>

      {dialogo?.tipo === 'nueva' && <NuevaSolicitud onCerrar={() => setDialogo(null)} onListo={listo} />}
      {dialogo?.tipo === 'programar' && <Programar solicitud={dialogo.s} onCerrar={() => setDialogo(null)} onListo={() => { listo(); void cliente.invalidateQueries({ queryKey: ['capacitaciones'] }) }} />}
      {dialogo?.tipo === 'rechazar' && <Rechazar solicitud={dialogo.s} onCerrar={() => setDialogo(null)} onListo={listo} />}
      <Confirmar abierto={dialogo?.tipo === 'eliminar'} onCerrar={() => setDialogo(null)}
        onConfirmar={() => dialogo && dialogo.tipo === 'eliminar' && eliminar.mutate(dialogo.s.id)} cargando={eliminar.isPending} error={eliminar.error} titulo="¿Eliminar la solicitud?">
        No se puede deshacer.
      </Confirmar>
    </div>
  )
}

function NuevaSolicitud({ onCerrar, onListo }: { onCerrar: () => void; onListo: () => void }) {
  const membresia = useMembresia(null)
  const form = useFormulario({ modulo: '', descripcion: '', participantesEstimados: '', fechaPreferida: '' })
  const v = form.valores
  const crear = useMutation({
    mutationFn: () =>
      api<Solicitud>('/api/solicitudes-capacitacion', {
        metodo: 'POST',
        cuerpo: { modulo: v.modulo || null, descripcion: v.descripcion, participantesEstimados: numero(v.participantesEstimados), fechaPreferida: nulo(v.fechaPreferida) },
      }),
    onSuccess: onListo,
    onError: form.fallo,
  })
  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo="Solicitar capacitación" textoEnviar="Enviar solicitud" cargando={crear.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); crear.mutate(undefined) }}>
      <Selector {...form.campo('modulo')} etiqueta="Módulo" obligatorio vacio="Selecciona" opciones={opcionesModulo((membresia.data?.modulosContratados ?? []) as ModuloErp[])}
        ayuda="Solo los módulos que tu empresa contrató." />
      <AreaTexto {...form.campo('descripcion')} etiqueta="¿Qué necesitan aprender?" obligatorio rows={4} />
      <div className="grid gap-4 sm:grid-cols-2">
        <CampoTexto {...form.campo('participantesEstimados')} etiqueta="Participantes" inputMode="numeric" obligatorio />
        <CampoTexto {...form.campo('fechaPreferida')} etiqueta="Fecha preferida" type="date" min={hoyLocal()} />
      </div>
    </DialogoFormulario>
  )
}

function Programar({ solicitud: s, onCerrar, onListo }: { solicitud: Solicitud; onCerrar: () => void; onListo: () => void }) {
  const [instructor, setInstructor] = useState<UsuarioElegido | null>(null)
  const form = useFormulario({
    titulo: `${ETIQUETA_MODULO[s.modulo]} para ${s.empresaNombre}`,
    fechaInicio: s.fechaPreferida ? `${s.fechaPreferida}T09:00` : '',
    duracionMinutos: '120',
    cupo: String(s.participantesEstimados),
    instructorId: '',
  })
  const v = form.valores
  const programar = useMutation({
    mutationFn: () =>
      api<unknown>(`/api/solicitudes-capacitacion/${s.id}/programar`, {
        metodo: 'PUT',
        cuerpo: { titulo: v.titulo, fechaInicio: desdeFechaHoraLocal(v.fechaInicio), duracionMinutos: numero(v.duracionMinutos), cupo: numero(v.cupo), instructorId: instructor?.id ?? null },
      }),
    onSuccess: onListo,
    onError: form.fallo,
  })
  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo="Programar capacitación" descripcion={`${s.empresaNombre} · ${ETIQUETA_MODULO[s.modulo]}`}
      textoEnviar="Programar" cargando={programar.isPending} error={form.general} onEnviar={() => { form.limpiar(); programar.mutate(undefined) }}>
      <CampoTexto {...form.campo('titulo')} etiqueta="Título" obligatorio />
      <div className="grid gap-4 sm:grid-cols-3">
        <CampoTexto {...form.campo('fechaInicio')} etiqueta="Inicio (hora de Bogotá)" type="datetime-local" obligatorio />
        <CampoTexto {...form.campo('duracionMinutos')} etiqueta="Duración (min)" inputMode="numeric" obligatorio />
        <CampoTexto {...form.campo('cupo')} etiqueta="Cupo" inputMode="numeric" obligatorio />
      </div>
      <BuscadorUsuario etiqueta="Instructor" obligatorio tipo="personal" valor={instructor} onCambiar={setInstructor} error={form.errores.instructorId} />
    </DialogoFormulario>
  )
}

function Rechazar({ solicitud: s, onCerrar, onListo }: { solicitud: Solicitud; onCerrar: () => void; onListo: () => void }) {
  const form = useFormulario({ motivo: '' })
  const rechazar = useMutation({
    mutationFn: () => api<Solicitud>(`/api/solicitudes-capacitacion/${s.id}/rechazar`, { metodo: 'PUT', cuerpo: { motivo: form.valores.motivo } }),
    onSuccess: onListo,
    onError: form.fallo,
  })
  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo="Rechazar solicitud" descripcion={s.empresaNombre} textoEnviar="Rechazar" peligro
      cargando={rechazar.isPending} error={form.general} onEnviar={() => { form.limpiar(); rechazar.mutate(undefined) }}>
      <AreaTexto {...form.campo('motivo')} etiqueta="Motivo" obligatorio rows={3} ayuda="La empresa verá este motivo." />
    </DialogoFormulario>
  )
}
