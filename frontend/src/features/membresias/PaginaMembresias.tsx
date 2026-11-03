import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import { useMembresia, useNombresEmpresas, usePlanes, type Membresia } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import {
  Aviso, Boton, Cargando, CampoTexto, Confirmar, DialogoFormulario, Encabezado, Filtros, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import {
  COLOR_MODULO, ESTADO_MEMBRESIA, ETIQUETA_MODULO, MODULOS_ERP, MOTIVO_SIN_SOPORTE, aFechaLocal, desdeFechaLocal, formato, type EstadoMembresia, type ModuloErp,
} from '@/lib/dominio'
import { useFormulario } from '@/lib/formulario'

/** Membresías (HU13 a HU16). El estado VENCIDA se deriva de la fecha (estadoVisible). */
export function PaginaMembresias() {
  const { esPersonal } = usePermisos()
  return esPersonal ? <ListaMembresias /> : <MiMembresia />
}

function Modulos({ modulos }: { modulos: ModuloErp[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {modulos.map((m) => <Insignia key={m} tono="primario" punto={COLOR_MODULO[m]}>{ETIQUETA_MODULO[m]}</Insignia>)}
    </div>
  )
}

function MiMembresia() {
  const { puede } = usePermisos()
  const q = useMembresia(null)
  const planes = usePlanes(puede('PLANES_VER'))
  if (q.isPending) return <Cargando />
  if (q.isError) return <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso>
  const m = q.data
  if (!m) return <Vacio titulo="Tu empresa no tiene membresía">Habla con Sistemas Integrados para contratar un plan.</Vacio>
  const plan = planes.planes.find((p) => p.id === m.planId)
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Encabezado titulo="Mi membresía" descripcion={plan ? `Plan ${plan.nombre}` : undefined} />
      {m.soporte.habilitado ? (
        <Aviso tono="exito">Tu soporte está activo: puedes crear tickets y solicitar capacitaciones.</Aviso>
      ) : (
        <Aviso tono="peligro" titulo="Soporte bloqueado">No puedes crear tickets porque {MOTIVO_SIN_SOPORTE[m.soporte.motivo ?? ''] ?? 'la membresía no está vigente'}.</Aviso>
      )}
      <Tarjeta>
        <dl className="grid gap-4 text-sm sm:grid-cols-3">
          <div><dt className="text-texto-suave">Estado</dt><dd className="mt-1"><Insignia tono={ESTADO_MEMBRESIA[m.estadoVisible].tono}>{ESTADO_MEMBRESIA[m.estadoVisible].etiqueta}</Insignia></dd></div>
          <div><dt className="text-texto-suave">Inicio</dt><dd className="font-medium">{formato.fecha(m.fechaInicio)}</dd></div>
          <div><dt className="text-texto-suave">Vencimiento</dt><dd className="font-medium">{formato.fecha(m.fechaVencimiento)}</dd></div>
          <div className="sm:col-span-3"><dt className="mb-1 text-texto-suave">Módulos contratados</dt><dd><Modulos modulos={m.modulosContratados} /></dd></div>
          {plan && (
            <div className="sm:col-span-3">
              <dt className="text-texto-suave">Tu plan incluye</dt>
              <dd className="font-medium">
                Respuesta en {plan.limites.horasRespuesta} h · {plan.limites.capacitacionesIncluidas} capacitaciones · hasta {plan.limites.maxUsuarios} usuarios
              </dd>
            </div>
          )}
        </dl>
      </Tarjeta>
    </div>
  )
}

function ListaMembresias() {
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const empresas = useNombresEmpresas(true)
  const planes = usePlanes(true)
  const [pagina, setPagina] = useState(0)
  const [f, setF] = useState({ empresaId: '', planId: '', estado: '' })
  const [editar, setEditar] = useState<Membresia | 'nueva' | null>(null)
  const [borrar, setBorrar] = useState<Membresia | null>(null)
  const filtrar = (c: Partial<typeof f>) => { setF((v) => ({ ...v, ...c })); setPagina(0) }

  const params = { ...f, pagina }
  const q = useQuery({
    queryKey: ['membresias', 'lista', params],
    queryFn: ({ signal }) => api<Pagina<Membresia>>('/api/membresias', { params, signal }),
    placeholderData: (anterior) => anterior,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/membresias/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => { setBorrar(null); void cliente.invalidateQueries({ queryKey: ['membresias'] }) },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Membresías" descripcion="Plan, módulos contratados y vigencia de cada empresa."
        acciones={puede('MEMBRESIAS_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nueva')}>Nueva membresía</Boton>} />
      <Filtros>
        <Selector etiqueta="Empresa" vacio="Todas" value={f.empresaId} onChange={(e) => filtrar({ empresaId: e.target.value })} opciones={empresas.opciones} />
        <Selector etiqueta="Plan" vacio="Todos" value={f.planId} onChange={(e) => filtrar({ planId: e.target.value })} opciones={planes.planes.map((p) => ({ valor: p.id, etiqueta: p.nombre }))} />
        <Selector etiqueta="Estado" vacio="Todos" value={f.estado} onChange={(e) => filtrar({ estado: e.target.value })}
          opciones={(['PENDIENTE', 'ACTIVA', 'SUSPENDIDA'] as const).map((e) => ({ valor: e, etiqueta: ESTADO_MEMBRESIA[e].etiqueta }))} />
      </Filtros>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay membresías" /> : (
          <>
            <Tabla>
              <thead><tr><Th>Empresa</Th><Th>Plan</Th><Th>Estado</Th><Th>Módulos</Th><Th>Vigencia</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((m) => (
                  <Tr key={m.id}>
                    <Td className="font-medium">{empresas.nombre(m.empresaId)}</Td>
                    <Td>{planes.nombre(m.planId)}</Td>
                    <Td><Insignia tono={ESTADO_MEMBRESIA[m.estadoVisible].tono}>{ESTADO_MEMBRESIA[m.estadoVisible].etiqueta}</Insignia></Td>
                    <Td><Modulos modulos={m.modulosContratados} /></Td>
                    <Td className="whitespace-nowrap text-texto-suave">{formato.fecha(m.fechaInicio)} – {formato.fecha(m.fechaVencimiento)}</Td>
                    <Td className="text-right whitespace-nowrap">
                      {puede('MEMBRESIAS_EDITAR') && <Boton variante="fantasma" tamano="sm" aria-label="Editar membresía" onClick={() => setEditar(m)}><Pencil aria-hidden className="size-4" /></Boton>}
                      {puede('MEMBRESIAS_ELIMINAR') && <Boton variante="fantasma" tamano="sm" aria-label="Eliminar membresía" onClick={() => { eliminar.reset(); setBorrar(m) }}><Trash2 aria-hidden className="size-4" /></Boton>}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
      {editar && (
        <FormularioMembresia membresia={editar === 'nueva' ? undefined : editar} empresas={empresas.opciones}
          planes={planes.planes.filter((p) => p.activo || (editar !== 'nueva' && p.id === editar.planId)).map((p) => ({ valor: p.id, etiqueta: `${p.nombre} (hasta ${p.limites.maxModulos} módulos)` }))}
          onCerrar={() => setEditar(null)} />
      )}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo="¿Eliminar la membresía?">
        Una membresía con pagos registrados no se elimina.
      </Confirmar>
    </div>
  )
}

type Opcion = { valor: string; etiqueta: string }

function FormularioMembresia({ membresia, empresas, planes, onCerrar }: { membresia?: Membresia; empresas: Opcion[]; planes: Opcion[]; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const form = useFormulario({
    empresaId: membresia?.empresaId ?? '',
    planId: membresia?.planId ?? '',
    modulosContratados: membresia?.modulosContratados ?? ([] as ModuloErp[]),
    fechaInicio: aFechaLocal(membresia?.fechaInicio),
    fechaVencimiento: aFechaLocal(membresia?.fechaVencimiento),
    estado: (membresia?.estado as string) ?? 'PENDIENTE',
  })
  const v = form.valores
  const guardar = useMutation({
    mutationFn: () => {
      const comun = { planId: v.planId || null, modulosContratados: v.modulosContratados, fechaInicio: desdeFechaLocal(v.fechaInicio), fechaVencimiento: desdeFechaLocal(v.fechaVencimiento) }
      return membresia
        ? api<Membresia>(`/api/membresias/${membresia.id}`, { metodo: 'PUT', cuerpo: { ...comun, estado: v.estado } })
        : api<Membresia>('/api/membresias', { metodo: 'POST', cuerpo: { ...comun, empresaId: v.empresaId || null } })
    },
    onSuccess: () => { void cliente.invalidateQueries({ queryKey: ['membresias'] }); onCerrar() },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={membresia ? 'Editar membresía' : 'Nueva membresía'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      {!membresia && <Selector {...form.campo('empresaId')} etiqueta="Empresa" obligatorio vacio="Selecciona" opciones={empresas} />}
      <div className="grid gap-4 sm:grid-cols-2">
        <Selector {...form.campo('planId')} etiqueta="Plan" obligatorio vacio="Selecciona" opciones={planes} />
        {membresia && (
          <Selector {...form.campo('estado')} etiqueta="Estado"
            opciones={(['PENDIENTE', 'ACTIVA', 'SUSPENDIDA'] as EstadoMembresia[]).map((e) => ({ valor: e, etiqueta: ESTADO_MEMBRESIA[e].etiqueta }))} />
        )}
        <CampoTexto {...form.campo('fechaInicio')} etiqueta="Inicio" type="date" obligatorio />
        <CampoTexto {...form.campo('fechaVencimiento')} etiqueta="Vencimiento" type="date" obligatorio />
      </div>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-medium">Módulos contratados {form.errores.modulosContratados && <span className="ml-2 text-xs text-peligro-texto">{form.errores.modulosContratados}</span>}</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {MODULOS_ERP.map((m) => (
            <label key={m} className="flex cursor-pointer items-center gap-2 text-sm">
              <input type="checkbox" className="size-4 accent-primario" checked={v.modulosContratados.includes(m)}
                onChange={(e) => form.poner('modulosContratados', e.target.checked ? [...v.modulosContratados, m] : v.modulosContratados.filter((x) => x !== m))} />
              {ETIQUETA_MODULO[m]}
            </label>
          ))}
        </div>
      </fieldset>
      {!membresia && <p className="text-xs text-texto-suave">Nace pendiente: pasa a activa o suspendida con los pagos que se registren, o a mano desde aquí.</p>}
    </DialogoFormulario>
  )
}
