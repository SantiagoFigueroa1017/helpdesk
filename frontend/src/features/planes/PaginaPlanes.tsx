import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import type { Plan } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { AreaTexto, Aviso, Boton, CampoTexto, Cargando, Casilla, Confirmar, DialogoFormulario, Encabezado, Insignia, Selector, Vacio } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { ETIQUETA_NIVEL, formato } from '@/lib/dominio'
import { numero, useFormulario } from '@/lib/formulario'

/** Planes de membresía (HU09 a HU12). El cambio de horasRespuesta no altera los tickets ya creados. */
export function PaginaPlanes() {
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const [editar, setEditar] = useState<Plan | 'nuevo' | null>(null)
  const [borrar, setBorrar] = useState<Plan | null>(null)
  const q = useQuery({ queryKey: ['planes', 'todos'], queryFn: ({ signal }) => api<Pagina<Plan>>('/api/planes', { params: { tamano: 100, orden: 'nombre' }, signal }) })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/planes/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => { setBorrar(null); void cliente.invalidateQueries({ queryKey: ['planes'] }) },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Planes" descripcion="Cada plan define el tiempo de respuesta, las capacitaciones incluidas y los límites de la membresía."
        acciones={puede('PLANES_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nuevo')}>Nuevo plan</Boton>} />
      {q.isPending ? <Cargando /> : q.isError ? <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso> : q.data.contenido.length === 0 ? <Vacio titulo="No hay planes" /> : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {q.data.contenido.map((p) => (
            <article key={p.id} className="flex flex-col rounded-tarjeta border border-borde bg-superficie p-5 shadow-tarjeta">
              <div className="flex items-center justify-between gap-2">
                <Insignia tono="primario">{ETIQUETA_NIVEL[p.nivelAcceso]}</Insignia>
                {!p.activo && <Insignia>Inactivo</Insignia>}
              </div>
              <h2 className="mt-3 text-lg font-semibold">{p.nombre}</h2>
              <p className="text-2xl font-semibold tabular-nums">{formato.pesos(p.precio)}<span className="text-sm font-normal text-texto-suave"> / mes</span></p>
              {p.descripcion && <p className="mt-2 text-sm text-texto-suave">{p.descripcion}</p>}
              <ul className="mt-4 flex flex-col gap-1.5 text-sm">
                <li>Respuesta en <strong>{p.limites.horasRespuesta} h</strong></li>
                <li><strong>{p.limites.capacitacionesIncluidas}</strong> capacitaciones incluidas</li>
                <li>Hasta <strong>{p.limites.maxUsuarios}</strong> usuarios</li>
                <li>Hasta <strong>{p.limites.maxModulos}</strong> módulos</li>
              </ul>
              {(puede('PLANES_EDITAR') || puede('PLANES_ELIMINAR')) && (
                <div className="mt-auto flex gap-2 pt-4">
                  {puede('PLANES_EDITAR') && <Boton variante="secundario" tamano="sm" icono={<Pencil aria-hidden className="size-4" />} onClick={() => setEditar(p)}>Editar</Boton>}
                  {puede('PLANES_ELIMINAR') && <Boton variante="fantasma" tamano="sm" aria-label={`Eliminar ${p.nombre}`} onClick={() => { eliminar.reset(); setBorrar(p) }}><Trash2 aria-hidden className="size-4" /></Boton>}
                </div>
              )}
            </article>
          ))}
        </div>
      )}
      {editar && <FormularioPlan plan={editar === 'nuevo' ? undefined : editar} onCerrar={() => setEditar(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo={`¿Eliminar el plan ${borrar?.nombre ?? ''}?`}>
        Un plan con membresías no se elimina: desactívalo para que no se pueda contratar.
      </Confirmar>
    </div>
  )
}

function FormularioPlan({ plan, onCerrar }: { plan?: Plan; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const l = plan?.limites
  const form = useFormulario({
    nombre: plan?.nombre ?? '',
    descripcion: plan?.descripcion ?? '',
    precio: plan ? String(plan.precio) : '',
    nivelAcceso: (plan?.nivelAcceso as string) ?? 'BASICO',
    limites: '',
    horasRespuesta: l ? String(l.horasRespuesta) : '',
    capacitacionesIncluidas: l ? String(l.capacitacionesIncluidas) : '',
    maxUsuarios: l ? String(l.maxUsuarios) : '',
    maxModulos: l ? String(l.maxModulos) : '',
    activo: plan?.activo ?? true,
  })
  const v = form.valores
  const guardar = useMutation({
    mutationFn: () =>
      api<Plan>(plan ? `/api/planes/${plan.id}` : '/api/planes', {
        metodo: plan ? 'PUT' : 'POST',
        cuerpo: {
          nombre: v.nombre,
          descripcion: v.descripcion || null,
          precio: numero(v.precio),
          nivelAcceso: v.nivelAcceso,
          limites: {
            horasRespuesta: numero(v.horasRespuesta),
            capacitacionesIncluidas: numero(v.capacitacionesIncluidas),
            maxUsuarios: numero(v.maxUsuarios),
            maxModulos: numero(v.maxModulos),
          },
          ...(plan ? { activo: v.activo } : {}),
        },
      }),
    onSuccess: () => { void cliente.invalidateQueries({ queryKey: ['planes'] }); onCerrar() },
    onError: form.fallo,
  })
  const lim = (c: 'horasRespuesta' | 'capacitacionesIncluidas' | 'maxUsuarios' | 'maxModulos') => ({ ...form.campo(c), error: form.errores[`limites.${c}`], inputMode: 'numeric' as const })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={plan ? `Editar ${plan.nombre}` : 'Nuevo plan'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      <div className="grid gap-4 sm:grid-cols-2">
        <CampoTexto {...form.campo('nombre')} etiqueta="Nombre" obligatorio />
        <Selector {...form.campo('nivelAcceso')} etiqueta="Nivel de acceso" opciones={Object.entries(ETIQUETA_NIVEL).map(([valor, etiqueta]) => ({ valor, etiqueta }))} />
        <CampoTexto {...form.campo('precio')} etiqueta="Precio mensual (COP)" obligatorio inputMode="decimal" />
        <CampoTexto {...lim('horasRespuesta')} etiqueta="Horas de respuesta" obligatorio />
        <CampoTexto {...lim('capacitacionesIncluidas')} etiqueta="Capacitaciones incluidas" obligatorio />
        <CampoTexto {...lim('maxUsuarios')} etiqueta="Máximo de usuarios" obligatorio />
        <CampoTexto {...lim('maxModulos')} etiqueta="Máximo de módulos" obligatorio ayuda="De 1 a 5." />
      </div>
      <AreaTexto {...form.campo('descripcion')} etiqueta="Descripción" rows={2} />
      {plan && <Casilla etiqueta="Plan activo (se puede contratar)" checked={v.activo} onChange={(e) => form.poner('activo', e.target.checked)} />}
    </DialogoFormulario>
  )
}
