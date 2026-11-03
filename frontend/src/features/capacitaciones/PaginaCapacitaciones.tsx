import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Pencil, Plus, Trash2, Users } from 'lucide-react'

import { useNombresEmpresas } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { BuscadorUsuario, type UsuarioElegido } from '@/components/BuscadorUsuario'
import {
  Aviso, Boton, CampoTexto, Cargando, Casilla, Confirmar, DialogoFormulario, Encabezado, Filtros, Insignia, Paginador, Selector, Tarjeta, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import {
  COLOR_MODULO, ESTADO_CAPACITACION, ETIQUETA_MODULO, aFechaHoraLocal, desdeFechaHoraLocal, formato, hoyLocal, opcionesModulo,
} from '@/lib/dominio'
import { numero, useFormulario } from '@/lib/formulario'

import { Inscritos } from './Inscritos'
import type { Capacitacion } from './tipos'

/**
 * Capacitaciones (HU33 a HU40). Masivas para todos los clientes y personalizadas para una empresa.
 * El cliente se inscribe o cancela; el personal programa, edita, ve los inscritos y marca asistencia.
 */
export function PaginaCapacitaciones() {
  const { puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [f, setF] = useState({ modulo: '', tipo: '', vista: 'proximas' })
  const [editar, setEditar] = useState<Capacitacion | 'nueva' | null>(null)
  const [borrar, setBorrar] = useState<Capacitacion | null>(null)
  const [inscritos, setInscritos] = useState<Capacitacion | null>(null)
  const [error, setError] = useState<unknown>(null)
  const filtrar = (c: Partial<typeof f>) => { setF((v) => ({ ...v, ...c })); setPagina(0) }

  const params = {
    modulo: f.modulo,
    tipo: f.tipo,
    desde: f.vista === 'proximas' ? hoyLocal() : undefined,
    orden: f.vista === 'proximas' ? 'fechaInicio,asc' : 'fechaInicio,desc',
    pagina,
  }
  const q = useQuery({
    queryKey: ['capacitaciones', 'lista', params],
    queryFn: ({ signal }) => api<Pagina<Capacitacion>>('/api/capacitaciones', { params, signal }),
    placeholderData: (anterior) => anterior,
  })
  const refrescar = () => cliente.invalidateQueries({ queryKey: ['capacitaciones'] })

  const inscribir = useMutation({
    mutationFn: (c: Capacitacion) =>
      c.miInscripcionId
        ? api<void>(`/api/inscripciones/${c.miInscripcionId}`, { metodo: 'DELETE' })
        : api<unknown>(`/api/capacitaciones/${c.id}/inscripciones`, { metodo: 'POST', cuerpo: {} }),
    onSuccess: () => { setError(null); void refrescar(); void cliente.invalidateQueries({ queryKey: ['inscripciones'] }) },
    onError: setError,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<{ inscripcionesEliminadas: number; solicitudDevuelta: boolean }>(`/api/capacitaciones/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => { setBorrar(null); void refrescar(); void cliente.invalidateQueries({ queryKey: ['solicitudes'] }) },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Capacitaciones" descripcion={esPersonal ? 'Cronograma de sesiones masivas y personalizadas.' : 'Sesiones abiertas a todos los clientes y las programadas para tu empresa.'}
        acciones={puede('CAPACITACIONES_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nueva')}>Programar</Boton>} />
      <Filtros>
        <Selector etiqueta="Mostrar" value={f.vista} onChange={(e) => filtrar({ vista: e.target.value })}
          opciones={[{ valor: 'proximas', etiqueta: 'Desde hoy' }, { valor: 'todas', etiqueta: 'Todas (recientes primero)' }]} />
        <Selector etiqueta="Módulo" vacio="Todos" value={f.modulo} onChange={(e) => filtrar({ modulo: e.target.value })} opciones={opcionesModulo()} />
        <Selector etiqueta="Tipo" vacio="Todos" value={f.tipo} onChange={(e) => filtrar({ tipo: e.target.value })}
          opciones={[{ valor: 'MASIVA', etiqueta: 'Masiva' }, { valor: 'PERSONALIZADA', etiqueta: 'Personalizada' }]} />
      </Filtros>
      {error != null && <Aviso tono="peligro">{mensajeDeError(error)}</Aviso>}

      {q.isPending ? <Cargando /> : q.isError ? <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso> : q.data.contenido.length === 0 ? (
        <Vacio titulo="No hay capacitaciones">{f.vista === 'proximas' ? 'No hay sesiones desde hoy con esos filtros.' : undefined}</Vacio>
      ) : (
        <div className="flex flex-col gap-3">
          <ul className="grid gap-3 lg:grid-cols-2">
            {q.data.contenido.map((c) => {
              const estado = ESTADO_CAPACITACION[c.estadoVisible]
              const abierta = c.estadoVisible === 'PROGRAMADA'
              return (
                <li key={c.id} className="flex flex-col rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta">
                  <div className="flex flex-wrap items-center gap-2">
                    <Insignia tono={estado.tono}>{estado.etiqueta}</Insignia>
                    <Insignia tono="primario" punto={COLOR_MODULO[c.modulo]}>{ETIQUETA_MODULO[c.modulo]}</Insignia>
                    <Insignia>{c.tipo === 'MASIVA' ? 'Masiva' : `Personalizada · ${c.empresaNombre ?? ''}`}</Insignia>
                    {c.miInscripcionId && <Insignia tono="exito">Inscrito</Insignia>}
                  </div>
                  <h2 className="mt-2 font-semibold">{c.titulo}</h2>
                  <p className="mt-1 flex items-center gap-1.5 text-sm text-texto-suave">
                    <CalendarClock aria-hidden className="size-4" /> {formato.fechaHora(c.fechaInicio)} · {c.duracionMinutos} min · {c.instructorNombre}
                  </p>
                  <div className="mt-3">
                    <div className="flex justify-between text-xs text-texto-suave">
                      <span>{c.cuposOcupados} de {c.cupo} cupos</span>
                      <span>{c.cuposDisponibles} libres</span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-superficie-2" aria-hidden>
                      <div className="h-full rounded-full bg-primario" style={{ width: `${Math.min(100, (c.cuposOcupados / Math.max(1, c.cupo)) * 100)}%` }} />
                    </div>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {puede('INSCRIPCIONES_PROPIAS') && c.tipo === 'MASIVA' && abierta && (c.miInscripcionId || c.cuposDisponibles > 0) && (
                      <Boton tamano="sm" variante={c.miInscripcionId ? 'secundario' : 'primario'} cargando={inscribir.isPending && inscribir.variables?.id === c.id}
                        onClick={() => inscribir.mutate(c)}>
                        {c.miInscripcionId ? 'Cancelar inscripción' : 'Inscribirme'}
                      </Boton>
                    )}
                    {puede('INSCRIPCIONES_VER') && (
                      <Boton tamano="sm" variante="secundario" icono={<Users aria-hidden className="size-4" />} onClick={() => setInscritos(c)}>Inscritos</Boton>
                    )}
                    {puede('CAPACITACIONES_EDITAR') && c.estado === 'PROGRAMADA' && c.estadoVisible !== 'REALIZADA' && (
                      <Boton tamano="sm" variante="fantasma" icono={<Pencil aria-hidden className="size-4" />} onClick={() => setEditar(c)}>Editar</Boton>
                    )}
                    {puede('CAPACITACIONES_ELIMINAR') && (
                      <Boton tamano="sm" variante="fantasma" aria-label={`Eliminar ${c.titulo}`} onClick={() => { eliminar.reset(); setBorrar(c) }}><Trash2 aria-hidden className="size-4" /></Boton>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
          <Tarjeta sinRelleno>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </Tarjeta>
        </div>
      )}

      {editar && <FormularioCapacitacion capacitacion={editar === 'nueva' ? undefined : editar} onCerrar={() => setEditar(null)} />}
      {inscritos && <Inscritos capacitacion={inscritos} onCerrar={() => setInscritos(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo={`¿Eliminar «${borrar?.titulo ?? ''}»?`}>
        Se borran sus {borrar?.cuposOcupados ?? 0} inscripciones. Si nació de una solicitud, la solicitud vuelve a pendiente. Para avisar a los inscritos es mejor cancelarla.
      </Confirmar>
    </div>
  )
}

function FormularioCapacitacion({ capacitacion: c, onCerrar }: { capacitacion?: Capacitacion; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const empresas = useNombresEmpresas(!c)
  const [instructor, setInstructor] = useState<UsuarioElegido | null>(c ? { id: c.instructorId, nombre: c.instructorNombre, empresaId: null } : null)
  const form = useFormulario({
    titulo: c?.titulo ?? '',
    tipo: (c?.tipo as string) ?? 'MASIVA',
    modulo: (c?.modulo as string) ?? '',
    empresaId: c?.empresaId ?? '',
    fechaInicio: aFechaHoraLocal(c?.fechaInicio),
    duracionMinutos: c ? String(c.duracionMinutos) : '60',
    cupo: c ? String(c.cupo) : '',
    instructorId: '',
    cancelar: false,
  })
  const v = form.valores
  const guardar = useMutation({
    mutationFn: () => {
      const comun = { titulo: v.titulo, fechaInicio: desdeFechaHoraLocal(v.fechaInicio), duracionMinutos: numero(v.duracionMinutos), cupo: numero(v.cupo), instructorId: instructor?.id ?? null }
      return c
        ? api<Capacitacion>(`/api/capacitaciones/${c.id}`, { metodo: 'PUT', cuerpo: { ...comun, estado: v.cancelar ? 'CANCELADA' : 'PROGRAMADA' } })
        : api<Capacitacion>('/api/capacitaciones', {
            metodo: 'POST',
            cuerpo: { ...comun, tipo: v.tipo, modulo: v.modulo || null, empresaId: v.tipo === 'PERSONALIZADA' ? v.empresaId || null : null },
          })
    },
    onSuccess: () => { void cliente.invalidateQueries({ queryKey: ['capacitaciones'] }); onCerrar() },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={c ? 'Editar capacitación' : 'Programar capacitación'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      <CampoTexto {...form.campo('titulo')} etiqueta="Título" obligatorio />
      {!c && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Selector {...form.campo('tipo')} etiqueta="Tipo" opciones={[{ valor: 'MASIVA', etiqueta: 'Masiva: para todos los clientes' }, { valor: 'PERSONALIZADA', etiqueta: 'Personalizada: una empresa' }]} />
          <Selector {...form.campo('modulo')} etiqueta="Módulo" obligatorio vacio="Selecciona" opciones={opcionesModulo()} />
          {v.tipo === 'PERSONALIZADA' && (
            <div className="sm:col-span-2">
              <Selector {...form.campo('empresaId')} etiqueta="Empresa" obligatorio vacio="Selecciona" opciones={empresas.opciones} ayuda="Debe tener soporte activo y el módulo contratado." />
            </div>
          )}
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-3">
        <CampoTexto {...form.campo('fechaInicio')} etiqueta="Inicio (hora de Bogotá)" type="datetime-local" obligatorio />
        <CampoTexto {...form.campo('duracionMinutos')} etiqueta="Duración (min)" inputMode="numeric" obligatorio />
        <CampoTexto {...form.campo('cupo')} etiqueta="Cupo" inputMode="numeric" obligatorio />
      </div>
      <BuscadorUsuario etiqueta="Instructor" obligatorio tipo="personal" valor={instructor} onCambiar={setInstructor} error={form.errores.instructorId}
        ayuda="Personal con permiso para marcar asistencia." />
      {c && (
        <Casilla etiqueta="Cancelar la capacitación (definitivo: ya no se podrá editar)" checked={v.cancelar} onChange={(e) => form.poner('cancelar', e.target.checked)} />
      )}
    </DialogoFormulario>
  )
}
