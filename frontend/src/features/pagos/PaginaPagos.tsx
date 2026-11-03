import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import { useMembresia, useNombresEmpresas } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import {
  Aviso, Boton, CampoTexto, Cargando, Confirmar, DialogoFormulario, Encabezado, Filtros, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio,
} from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { ETIQUETA_METODO, aFechaHoraLocal, desdeFechaHoraLocal, formato, type MetodoPago } from '@/lib/dominio'
import { numero, useFormulario } from '@/lib/formulario'

type Pago = {
  id: string
  membresiaId: string
  empresaId: string
  monto: number
  fechaPago: string
  metodoPago: MetodoPago
  referencia: string
  resultado: 'APROBADO' | 'FALLIDO'
}

/** Pagos de las membresías (HU17 a HU20). Un pago aprobado activa la membresía; uno fallido la suspende. */
export function PaginaPagos() {
  const { puede, esPersonal } = usePermisos()
  const cliente = useQueryClient()
  const empresas = useNombresEmpresas(esPersonal)
  const [pagina, setPagina] = useState(0)
  const [f, setF] = useState({ empresaId: '', resultado: '' })
  const [editar, setEditar] = useState<Pago | 'nuevo' | null>(null)
  const [borrar, setBorrar] = useState<Pago | null>(null)
  const filtrar = (c: Partial<typeof f>) => { setF((v) => ({ ...v, ...c })); setPagina(0) }

  const params = { ...f, pagina, orden: 'fechaPago,desc' }
  const q = useQuery({
    queryKey: ['pagos', 'lista', params],
    queryFn: ({ signal }) => api<Pagina<Pago>>('/api/pagos', { params, signal }),
    placeholderData: (anterior) => anterior,
  })
  const eliminar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/pagos/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => { setBorrar(null); void cliente.invalidateQueries({ queryKey: ['pagos'] }); void cliente.invalidateQueries({ queryKey: ['membresias'] }) },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Pagos" descripcion={esPersonal ? 'Pagos de las membresías de todas las empresas.' : 'Pagos de la membresía de tu empresa.'}
        acciones={puede('PAGOS_REGISTRAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setEditar('nuevo')}>Registrar pago</Boton>} />
      <Filtros>
        {esPersonal && <Selector etiqueta="Empresa" vacio="Todas" value={f.empresaId} onChange={(e) => filtrar({ empresaId: e.target.value })} opciones={empresas.opciones} />}
        <Selector etiqueta="Resultado" vacio="Todos" value={f.resultado} onChange={(e) => filtrar({ resultado: e.target.value })}
          opciones={[{ valor: 'APROBADO', etiqueta: 'Aprobados' }, { valor: 'FALLIDO', etiqueta: 'Fallidos' }]} />
      </Filtros>
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? <Vacio titulo="No hay pagos" /> : (
          <>
            <Tabla>
              <thead><tr><Th>Fecha</Th>{esPersonal && <Th>Empresa</Th>}<Th className="text-right">Monto</Th><Th>Método</Th><Th>Referencia</Th><Th>Resultado</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((p) => (
                  <Tr key={p.id}>
                    <Td className="whitespace-nowrap">{formato.fechaHora(p.fechaPago)}</Td>
                    {esPersonal && <Td className="font-medium">{empresas.nombre(p.empresaId)}</Td>}
                    <Td className="text-right tabular-nums">{formato.pesos(p.monto)}</Td>
                    <Td>{ETIQUETA_METODO[p.metodoPago]}</Td>
                    <Td className="font-mono text-xs">{p.referencia}</Td>
                    <Td><Insignia tono={p.resultado === 'APROBADO' ? 'exito' : 'peligro'}>{p.resultado === 'APROBADO' ? 'Aprobado' : 'Fallido'}</Insignia></Td>
                    <Td className="text-right whitespace-nowrap">
                      {puede('PAGOS_EDITAR') && <Boton variante="fantasma" tamano="sm" aria-label="Editar pago" onClick={() => setEditar(p)}><Pencil aria-hidden className="size-4" /></Boton>}
                      {puede('PAGOS_ELIMINAR') && <Boton variante="fantasma" tamano="sm" aria-label="Eliminar pago" onClick={() => { eliminar.reset(); setBorrar(p) }}><Trash2 aria-hidden className="size-4" /></Boton>}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
      {editar && <FormularioPago pago={editar === 'nuevo' ? undefined : editar} empresas={empresas.opciones} onCerrar={() => setEditar(null)} />}
      <Confirmar abierto={borrar !== null} onCerrar={() => setBorrar(null)} onConfirmar={() => borrar && eliminar.mutate(borrar.id)}
        cargando={eliminar.isPending} error={eliminar.error} titulo="¿Eliminar el pago?">
        El estado de la membresía no cambia al eliminar un pago. No se puede deshacer.
      </Confirmar>
    </div>
  )
}

function FormularioPago({ pago, empresas, onCerrar }: { pago?: Pago; empresas: { valor: string; etiqueta: string }[]; onCerrar: () => void }) {
  const cliente = useQueryClient()
  const [empresaId, setEmpresaId] = useState(pago?.empresaId ?? '')
  const membresia = useMembresia(empresaId, !pago && Boolean(empresaId))
  const form = useFormulario({
    membresiaId: pago?.membresiaId ?? '',
    monto: pago ? String(pago.monto) : '',
    fechaPago: aFechaHoraLocal(pago?.fechaPago ?? new Date().toISOString()),
    metodoPago: (pago?.metodoPago as string) ?? 'TRANSFERENCIA',
    referencia: pago?.referencia ?? '',
    resultado: (pago?.resultado as string) ?? 'APROBADO',
  })
  const v = form.valores
  const guardar = useMutation({
    mutationFn: () => {
      const comun = { monto: numero(v.monto), fechaPago: desdeFechaHoraLocal(v.fechaPago), metodoPago: v.metodoPago, referencia: v.referencia, resultado: v.resultado }
      return pago
        ? api<Pago>(`/api/pagos/${pago.id}`, { metodo: 'PUT', cuerpo: comun })
        : api<Pago>('/api/pagos', { metodo: 'POST', cuerpo: { ...comun, membresiaId: membresia.data?.id ?? null } })
    },
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['pagos'] })
      void cliente.invalidateQueries({ queryKey: ['membresias'] })
      onCerrar()
    },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario abierto onCerrar={onCerrar} titulo={pago ? 'Editar pago' : 'Registrar pago'} cargando={guardar.isPending} error={form.general}
      onEnviar={() => { form.limpiar(); guardar.mutate(undefined) }}>
      {!pago && (
        <Selector etiqueta="Empresa" obligatorio vacio="Selecciona" value={empresaId} onChange={(e) => setEmpresaId(e.target.value)} opciones={empresas}
          error={form.errores.membresiaId}
          ayuda={membresia.data === null ? 'Esta empresa no tiene membresía.' : membresia.data ? `Se registra en su membresía (${membresia.data.estadoVisible.toLowerCase()}).` : undefined} />
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <CampoTexto {...form.campo('monto')} etiqueta="Monto (COP)" obligatorio inputMode="decimal" />
        <CampoTexto {...form.campo('fechaPago')} etiqueta="Fecha y hora" type="datetime-local" obligatorio />
        <Selector {...form.campo('metodoPago')} etiqueta="Método" opciones={Object.entries(ETIQUETA_METODO).map(([valor, etiqueta]) => ({ valor, etiqueta }))} />
        <Selector {...form.campo('resultado')} etiqueta="Resultado" opciones={[{ valor: 'APROBADO', etiqueta: 'Aprobado' }, { valor: 'FALLIDO', etiqueta: 'Fallido' }]} />
      </div>
      <CampoTexto {...form.campo('referencia')} etiqueta="Referencia" obligatorio />
    </DialogoFormulario>
  )
}
