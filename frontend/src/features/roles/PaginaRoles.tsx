import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import {
  AreaTexto, Aviso, Boton, CampoTexto, Cargando, Casilla, Confirmar, DialogoFormulario, Encabezado, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { useFormulario } from '@/lib/formulario'

type Rol = {
  id: string
  nombre: string
  descripcion: string | null
  alcance: 'GLOBAL' | 'EMPRESA'
  permisos: string[]
  activo: boolean
  esSistema: boolean
  usuariosAsignados: number
}
type PermisoCatalogo = { codigo: string; grupo: string; descripcion: string }

/** Roles dinámicos (HU25 y HU26): crear roles con cualquier combinación de permisos del catálogo. */
export function PaginaRoles() {
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [alcance, setAlcance] = useState('')
  const [editar, setEditar] = useState<Rol | 'nuevo' | null>(null)
  const [borrar, setBorrar] = useState<Rol | null>(null)
  const gestiona = puede('ROLES_GESTIONAR')

  const q = useQuery({
    queryKey: ['roles', { pagina, alcance }],
    queryFn: ({ signal }) => api<Pagina<Rol>>('/api/roles', { params: { pagina, alcance, orden: 'nombre' }, signal }),
    placeholderData: (anterior) => anterior,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/roles/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => {
      setBorrar(null)
      void cliente.invalidateQueries({ queryKey: ['roles'] })
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado
        titulo="Roles y permisos"
        descripcion="Cada rol es un conjunto de permisos. Los menús y las acciones dependen de los permisos, no del nombre del rol."
        acciones={gestiona && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nuevo')}>Nuevo rol</Boton>}
      />
      <div className="max-w-xs">
        <Selector etiqueta="Alcance" vacio="Todos" value={alcance} onChange={(e) => { setAlcance(e.target.value); setPagina(0) }}
          opciones={[{ valor: 'GLOBAL', etiqueta: 'Global (personal)' }, { valor: 'EMPRESA', etiqueta: 'Empresa (clientes)' }]} />
      </div>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay roles" /> : (
          <>
            <Tabla>
              <thead><tr><Th>Rol</Th><Th>Alcance</Th><Th className="text-right">Permisos</Th><Th className="text-right">Usuarios</Th><Th>Estado</Th>{gestiona && <Th />}</tr></thead>
              <tbody>
                {q.data.contenido.map((r) => (
                  <Tr key={r.id}>
                    <Td>
                      <p className="font-medium">{r.nombre} {r.esSistema && <Insignia tono="info">Sistema</Insignia>}</p>
                      {r.descripcion && <p className="max-w-md text-xs text-texto-suave">{r.descripcion}</p>}
                    </Td>
                    <Td>{r.alcance === 'GLOBAL' ? 'Global' : 'Empresa'}</Td>
                    <Td className="text-right tabular-nums">{r.permisos.length}</Td>
                    <Td className="text-right tabular-nums">{r.usuariosAsignados}</Td>
                    <Td><Insignia tono={r.activo ? 'exito' : 'neutro'}>{r.activo ? 'Activo' : 'Inactivo'}</Insignia></Td>
                    {gestiona && (
                      <Td className="text-right whitespace-nowrap">
                        <Boton variante="fantasma" tamano="sm" aria-label={`Editar ${r.nombre}`} onClick={() => setEditar(r)}><Pencil aria-hidden className="size-4" /></Boton>
                        {!r.esSistema && (
                          <Boton variante="fantasma" tamano="sm" aria-label={`Eliminar ${r.nombre}`} onClick={() => { eliminar.reset(); setBorrar(r) }}><Trash2 aria-hidden className="size-4" /></Boton>
                        )}
                      </Td>
                    )}
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
      {editar && <FormularioRol rol={editar === 'nuevo' ? undefined : editar} onCerrar={() => setEditar(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo={`¿Eliminar el rol ${borrar?.nombre ?? ''}?`}>
        Solo se puede eliminar si ningún usuario lo tiene asignado.
      </Confirmar>
    </div>
  )
}

function FormularioRol({ rol, onCerrar }: { rol?: Rol; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const catalogo = useQuery({ queryKey: ['permisos'], queryFn: ({ signal }) => api<PermisoCatalogo[]>('/api/permisos', { signal }), staleTime: Infinity })
  const form = useFormulario({
    nombre: rol?.nombre ?? '',
    descripcion: rol?.descripcion ?? '',
    alcance: (rol?.alcance as string) ?? 'EMPRESA',
    activo: rol?.activo ?? true,
    permisos: rol?.permisos ?? ([] as string[]),
  })
  const v = form.valores
  const grupos = new Map<string, PermisoCatalogo[]>()
  for (const p of catalogo.data ?? []) grupos.set(p.grupo, [...(grupos.get(p.grupo) ?? []), p])
  const alternar = (codigo: string, si: boolean) =>
    form.poner('permisos', si ? [...v.permisos, codigo] : v.permisos.filter((p) => p !== codigo))

  const guardar = useMutation({
    mutationFn: () =>
      api<Rol>(rol ? `/api/roles/${rol.id}` : '/api/roles', {
        metodo: rol ? 'PUT' : 'POST',
        cuerpo: { nombre: v.nombre, descripcion: v.descripcion || null, alcance: v.alcance, permisos: v.permisos, ...(rol ? { activo: v.activo } : {}) },
      }),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['roles'] })
      onCerrar()
    },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={rol ? `Editar ${rol.nombre}` : 'Nuevo rol'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      <div className="grid gap-4 sm:grid-cols-2">
        <CampoTexto {...form.campo('nombre')} etiqueta="Nombre" obligatorio ayuda="En mayúsculas y con guion bajo, por ejemplo CONSULTA_REPORTES." />
        <Selector {...form.campo('alcance')} etiqueta="Alcance" opciones={[{ valor: 'EMPRESA', etiqueta: 'Empresa (clientes)' }, { valor: 'GLOBAL', etiqueta: 'Global (personal)' }]} />
      </div>
      <AreaTexto {...form.campo('descripcion')} etiqueta="Descripción" rows={2} />
      {rol && <Casilla etiqueta="Rol activo" checked={v.activo} onChange={(e) => form.poner('activo', e.target.checked)} />}
      <fieldset className="flex flex-col gap-3">
        <legend className="text-sm font-medium">Permisos ({v.permisos.length}){form.errores.permisos && <span className="ml-2 text-xs text-peligro-texto">{form.errores.permisos}</span>}</legend>
        {catalogo.isPending ? <Cargando /> : [...grupos].map(([grupo, permisos]) => (
          <div key={grupo} className="rounded-control border border-borde p-3">
            <p className="mb-2 text-xs font-semibold tracking-wide text-texto-suave uppercase">{grupo}</p>
            <div className="grid gap-1.5 sm:grid-cols-2">
              {permisos.map((p) => (
                <label key={p.codigo} className="flex cursor-pointer items-start gap-2 text-sm" title={p.codigo}>
                  <input type="checkbox" className="mt-0.5 size-4 accent-primario" checked={v.permisos.includes(p.codigo)} onChange={(e) => alternar(p.codigo, e.target.checked)} />
                  <span>{p.descripcion}</span>
                </label>
              ))}
            </div>
          </div>
        ))}
      </fieldset>
    </DialogoFormulario>
  )
}
