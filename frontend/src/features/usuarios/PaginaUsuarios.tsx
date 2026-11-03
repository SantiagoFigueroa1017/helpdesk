import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import { useNombresEmpresas, type Usuario } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import {
  Aviso, Boton, CampoTexto, Cargando, Casilla, Confirmar, DialogoFormulario, Encabezado, Filtros, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { useFormulario } from '@/lib/formulario'
import { useRetardo } from '@/lib/useRetardo'

type RolAsignable = { id: string; nombre: string; descripcion: string | null; alcance: string; permisos: string[] }

/**
 * Usuarios (HU05 a HU08). El personal administra a todos; el CLIENTE_ADMIN, a los de su empresa y
 * solo con roles de alcance EMPRESA. Solo se ofrecen los roles que el usuario puede asignar
 * (GET /api/roles/asignables): nadie da permisos que no tiene.
 */
export function PaginaUsuarios() {
  const { usuario, puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const empresas = useNombresEmpresas(esPersonal)
  const [pagina, setPagina] = useState(0)
  const [f, setF] = useState({ texto: '', empresaId: '', activo: '' })
  const texto = useRetardo(f.texto.trim(), 300)
  const [editar, setEditar] = useState<Usuario | 'nuevo' | null>(null)
  const [borrar, setBorrar] = useState<Usuario | null>(null)
  const filtrar = (c: Partial<typeof f>) => { setF((v) => ({ ...v, ...c })); setPagina(0) }

  const params = { pagina, texto, empresaId: f.empresaId, activo: f.activo, orden: 'nombre' }
  const q = useQuery({
    queryKey: ['usuarios', 'lista', params],
    queryFn: ({ signal }) => api<Pagina<Usuario>>('/api/usuarios', { params, signal }),
    placeholderData: (anterior) => anterior,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/usuarios/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => { setBorrar(null); void cliente.invalidateQueries({ queryKey: ['usuarios'] }) },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Usuarios" descripcion={esPersonal ? 'Personal de soporte y usuarios de las empresas.' : 'Usuarios de tu empresa. El plan limita cuántos pueden estar activos.'}
        acciones={puede('USUARIOS_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nuevo')}>Nuevo usuario</Boton>} />
      <Filtros>
        <CampoTexto etiqueta="Buscar" type="search" placeholder="Comienzo del nombre" value={f.texto} onChange={(e) => filtrar({ texto: e.target.value })} />
        {esPersonal && <Selector etiqueta="Empresa" vacio="Todas" value={f.empresaId} onChange={(e) => filtrar({ empresaId: e.target.value })} opciones={empresas.opciones} />}
        <Selector etiqueta="Estado" vacio="Todos" value={f.activo} onChange={(e) => filtrar({ activo: e.target.value })}
          opciones={[{ valor: 'true', etiqueta: 'Activos' }, { valor: 'false', etiqueta: 'Inactivos' }]} />
      </Filtros>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay usuarios con esos filtros" /> : (
          <>
            <Tabla>
              <thead><tr><Th>Usuario</Th>{esPersonal && <Th>Empresa</Th>}<Th>Roles</Th><Th>Estado</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((u) => (
                  <Tr key={u.id}>
                    <Td><p className="font-medium">{u.nombre}</p><p className="text-xs text-texto-suave">{u.email}</p></Td>
                    {esPersonal && <Td className="text-texto-suave">{u.empresaId ? empresas.nombre(u.empresaId) : <Insignia tono="primario">Personal</Insignia>}</Td>}
                    <Td><div className="flex flex-wrap gap-1">{u.roles.map((r) => <Insignia key={r.id}>{r.nombre}</Insignia>)}</div></Td>
                    <Td><Insignia tono={u.activo ? 'exito' : 'neutro'}>{u.activo ? 'Activo' : 'Inactivo'}</Insignia></Td>
                    <Td className="text-right whitespace-nowrap">
                      {puede('USUARIOS_EDITAR') && <Boton variante="fantasma" tamano="sm" aria-label={`Editar ${u.nombre}`} onClick={() => setEditar(u)}><Pencil aria-hidden className="size-4" /></Boton>}
                      {puede('USUARIOS_ELIMINAR') && u.id !== usuario?.id && (
                        <Boton variante="fantasma" tamano="sm" aria-label={`Eliminar ${u.nombre}`} onClick={() => { eliminar.reset(); setBorrar(u) }}><Trash2 aria-hidden className="size-4" /></Boton>
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
      {editar && <FormularioUsuario usuario={editar === 'nuevo' ? undefined : editar} empresas={empresas.opciones} onCerrar={() => setEditar(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo={`¿Eliminar a ${borrar?.nombre ?? ''}?`}>
        Si el usuario tiene tickets, comentarios o inscripciones no se puede eliminar: desactívalo en su lugar.
      </Confirmar>
    </div>
  )
}

function FormularioUsuario({ usuario, empresas, onCerrar }: { usuario?: Usuario; empresas: { valor: string; etiqueta: string }[]; onCerrar: () => void }) {
  const { esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const form = useFormulario({
    nombre: usuario?.nombre ?? '',
    email: usuario?.email ?? '',
    password: '',
    nuevaPassword: '',
    tipo: usuario ? (usuario.empresaId ? 'EMPRESA' : 'GLOBAL') : 'EMPRESA',
    empresaId: usuario?.empresaId ?? '',
    rolIds: usuario?.roles.map((r) => r.id) ?? ([] as string[]),
    activo: usuario?.activo ?? true,
  })
  const v = form.valores
  const alcance = esPersonal ? v.tipo : 'EMPRESA'
  const roles = useQuery({
    queryKey: ['roles', 'asignables', alcance],
    queryFn: ({ signal }) => api<RolAsignable[]>('/api/roles/asignables', { params: { alcance }, signal }),
  })

  const guardar = useMutation({
    mutationFn: () =>
      usuario
        ? api<Usuario>(`/api/usuarios/${usuario.id}`, {
            metodo: 'PUT',
            cuerpo: { nombre: v.nombre, email: v.email.toLowerCase(), rolIds: v.rolIds, activo: v.activo, nuevaPassword: v.nuevaPassword || null },
          })
        : api<Usuario>('/api/usuarios', {
            metodo: 'POST',
            cuerpo: { nombre: v.nombre, email: v.email.toLowerCase(), password: v.password, rolIds: v.rolIds, empresaId: alcance === 'EMPRESA' ? v.empresaId || null : null },
          }),
    onSuccess: () => { void cliente.invalidateQueries({ queryKey: ['usuarios'] }); onCerrar() },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={usuario ? `Editar a ${usuario.nombre}` : 'Nuevo usuario'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      <div className="grid gap-4 sm:grid-cols-2">
        <CampoTexto {...form.campo('nombre')} etiqueta="Nombre" obligatorio />
        <CampoTexto {...form.campo('email')} etiqueta="Correo" type="email" obligatorio />
      </div>
      {usuario ? (
        <CampoTexto {...form.campo('nuevaPassword')} etiqueta="Nueva contraseña" type="password" autoComplete="new-password" ayuda="Déjala vacía para no cambiarla. Al cambiarla se cierran sus sesiones." />
      ) : (
        <CampoTexto {...form.campo('password')} etiqueta="Contraseña" type="password" autoComplete="new-password" obligatorio ayuda="Entre 8 y 72 caracteres." />
      )}
      {esPersonal && !usuario && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Selector {...form.campo('tipo')} etiqueta="Tipo de usuario"
            opciones={[{ valor: 'EMPRESA', etiqueta: 'Usuario de una empresa' }, { valor: 'GLOBAL', etiqueta: 'Personal de Sistemas Integrados' }]}
            onChange={(e) => { form.poner('tipo', e.target.value); form.poner('rolIds', []) }} />
          {v.tipo === 'EMPRESA' && <Selector {...form.campo('empresaId')} etiqueta="Empresa" obligatorio vacio="Selecciona" opciones={empresas} />}
        </div>
      )}
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-medium">Roles {form.errores.rolIds && <span className="ml-2 text-xs text-peligro-texto">{form.errores.rolIds}</span>}</legend>
        {roles.isPending ? <Cargando /> : (roles.data ?? []).map((r) => (
          <label key={r.id} className="flex cursor-pointer items-start gap-2 rounded-control border border-borde p-2.5 text-sm hover:bg-superficie-2">
            <input type="checkbox" className="mt-0.5 size-4 accent-primario" checked={v.rolIds.includes(r.id)}
              onChange={(e) => form.poner('rolIds', e.target.checked ? [...v.rolIds, r.id] : v.rolIds.filter((x) => x !== r.id))} />
            <span><span className="font-medium">{r.nombre}</span>{r.descripcion && <span className="block text-xs text-texto-suave">{r.descripcion}</span>}</span>
          </label>
        ))}
      </fieldset>
      {usuario && <Casilla etiqueta="Usuario activo" checked={v.activo} onChange={(e) => form.poner('activo', e.target.checked)} />}
    </DialogoFormulario>
  )
}
