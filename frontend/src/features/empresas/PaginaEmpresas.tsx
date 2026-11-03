import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import type { Contacto, Empresa } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, Boton, CampoTexto, Cargando, Confirmar, DialogoFormulario, Encabezado, Paginador, Tabla, Tarjeta, Td, Th, Tr, Vacio } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { formato } from '@/lib/dominio'
import { nulo, useFormulario } from '@/lib/formulario'
import { useRetardo } from '@/lib/useRetardo'

/**
 * Empresas (HU01 a HU04). El personal ve todas; un usuario de empresa ve solo la suya ("Mi empresa")
 * y el CLIENTE_ADMIN edita el contacto (EMPRESAS_EDITAR_CONTACTO).
 */
export function PaginaEmpresas() {
  const { esPersonal } = usePermisos()
  return esPersonal ? <ListaEmpresas /> : <MiEmpresa />
}

function ListaEmpresas() {
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [texto, setTexto] = useState('')
  const buscado = useRetardo(texto.trim(), 300)
  const [editar, setEditar] = useState<Empresa | 'nueva' | null>(null)
  const [borrar, setBorrar] = useState<Empresa | null>(null)

  const q = useQuery({
    queryKey: ['empresas', 'lista', { pagina, buscado }],
    queryFn: ({ signal }) => api<Pagina<Empresa>>('/api/empresas', { params: { pagina, texto: buscado, orden: 'razonSocial' }, signal }),
    placeholderData: (anterior) => anterior,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/empresas/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => {
      setBorrar(null)
      void cliente.invalidateQueries({ queryKey: ['empresas'] })
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Empresas" descripcion="Clientes de Sistemas Integrados S.A."
        acciones={puede('EMPRESAS_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nueva')}>Nueva empresa</Boton>} />
      <div className="max-w-sm">
        <CampoTexto etiqueta="Buscar" type="search" placeholder="Razón social" value={texto} onChange={(e) => { setTexto(e.target.value); setPagina(0) }} />
      </div>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay empresas" /> : (
          <>
            <Tabla>
              <thead><tr><Th>Empresa</Th><Th>NIT</Th><Th>Contacto</Th><Th>Ciudad</Th><Th>Cliente desde</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((e) => (
                  <Tr key={e.id}>
                    <Td className="font-medium">{e.razonSocial}</Td>
                    <Td className="font-mono text-xs">{e.nit}</Td>
                    <Td><p>{e.contacto.nombre}</p><p className="text-xs text-texto-suave">{e.contacto.email} · {e.contacto.telefono}</p></Td>
                    <Td className="text-texto-suave">{e.contacto.ciudad ?? '—'}</Td>
                    <Td className="whitespace-nowrap text-texto-suave">{formato.fecha(e.fechaCreacion)}</Td>
                    <Td className="text-right whitespace-nowrap">
                      {puede('EMPRESAS_EDITAR') && <Boton variante="fantasma" tamano="sm" aria-label={`Editar ${e.razonSocial}`} onClick={() => setEditar(e)}><Pencil aria-hidden className="size-4" /></Boton>}
                      {puede('EMPRESAS_ELIMINAR') && <Boton variante="fantasma" tamano="sm" aria-label={`Eliminar ${e.razonSocial}`} onClick={() => { eliminar.reset(); setBorrar(e) }}><Trash2 aria-hidden className="size-4" /></Boton>}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
      {editar && <FormularioEmpresa empresa={editar === 'nueva' ? undefined : editar} soloContacto={false} onCerrar={() => setEditar(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo={`¿Eliminar ${borrar?.razonSocial ?? 'la empresa'}?`}>
        Solo se puede eliminar una empresa sin usuarios, membresías, tickets ni capacitaciones.
      </Confirmar>
    </div>
  )
}

function MiEmpresa() {
  const { puede } = usePermisos()
  const [editar, setEditar] = useState(false)
  const q = useQuery({ queryKey: ['empresas', 'propia'], queryFn: ({ signal }) => api<Pagina<Empresa>>('/api/empresas', { params: { tamano: 1 }, signal }) })
  if (q.isPending) return <Cargando />
  if (q.isError) return <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso>
  const e = q.data.contenido[0]
  if (!e) return <Vacio titulo="No encontramos tu empresa" />
  const c = e.contacto
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Encabezado titulo={e.razonSocial} descripcion={`NIT ${e.nit}`}
        acciones={puede('EMPRESAS_EDITAR_CONTACTO') && <Boton variante="secundario" icono={<Pencil aria-hidden className="size-4" />} onClick={() => setEditar(true)}>Editar contacto</Boton>} />
      <Tarjeta titulo="Contacto">
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          {([['Nombre', c.nombre], ['Correo', c.email], ['Teléfono', c.telefono], ['Dirección', c.direccion], ['Ciudad', c.ciudad]] as const).map(([t, v]) => (
            <div key={t}><dt className="text-texto-suave">{t}</dt><dd className="font-medium">{v || '—'}</dd></div>
          ))}
        </dl>
      </Tarjeta>
      {editar && <FormularioEmpresa empresa={e} soloContacto onCerrar={() => setEditar(false)} />}
    </div>
  )
}

function FormularioEmpresa({ empresa, soloContacto, onCerrar }: { empresa?: Empresa; soloContacto: boolean; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const c: Partial<Contacto> = empresa?.contacto ?? {}
  const form = useFormulario({
    nit: empresa?.nit ?? '',
    razonSocial: empresa?.razonSocial ?? '',
    contacto: '',
    nombre: c.nombre ?? '',
    email: c.email ?? '',
    telefono: c.telefono ?? '',
    direccion: c.direccion ?? '',
    ciudad: c.ciudad ?? '',
  })
  const v = form.valores
  const contacto = { nombre: v.nombre, email: v.email, telefono: v.telefono, direccion: nulo(v.direccion), ciudad: nulo(v.ciudad) }
  const guardar = useMutation({
    mutationFn: () =>
      soloContacto && empresa
        ? api<Empresa>(`/api/empresas/${empresa.id}/contacto`, { metodo: 'PUT', cuerpo: contacto })
        : api<Empresa>(empresa ? `/api/empresas/${empresa.id}` : '/api/empresas', {
            metodo: empresa ? 'PUT' : 'POST',
            cuerpo: { nit: v.nit, razonSocial: v.razonSocial, contacto },
          }),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['empresas'] })
      onCerrar()
    },
    onError: form.fallo,
  })
  // Los errores del contacto llegan como "contacto.email" (o "email" en PUT /contacto).
  const err = (campo: string) => form.errores[`contacto.${campo}`] ?? form.errores[campo]

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={soloContacto ? 'Editar contacto' : empresa ? 'Editar empresa' : 'Nueva empresa'}
      cargando={guardar.isPending} error={form.general} onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      {!soloContacto && (
        <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
          <CampoTexto {...form.campo('nit')} etiqueta="NIT" obligatorio placeholder="900123456-7" />
          <CampoTexto {...form.campo('razonSocial')} etiqueta="Razón social" obligatorio />
        </div>
      )}
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-sm font-semibold">Contacto</legend>
        <CampoTexto {...form.campo('nombre')} error={err('nombre')} etiqueta="Nombre" obligatorio />
        <CampoTexto {...form.campo('email')} error={err('email')} etiqueta="Correo" type="email" obligatorio />
        <CampoTexto {...form.campo('telefono')} error={err('telefono')} etiqueta="Teléfono" obligatorio inputMode="tel" />
        <CampoTexto {...form.campo('ciudad')} error={err('ciudad')} etiqueta="Ciudad" />
        <div className="sm:col-span-2"><CampoTexto {...form.campo('direccion')} error={err('direccion')} etiqueta="Dirección" /></div>
      </fieldset>
    </DialogoFormulario>
  )
}
