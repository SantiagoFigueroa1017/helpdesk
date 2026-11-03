import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router'

import { useMembresia } from '@/api/catalogos'
import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, Cargando, Casilla, Encabezado, Filtros, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { cn } from '@/lib/cn'
import {
  COLOR_MODULO,
  ESTADO_TICKET,
  ESTADOS_TICKET,
  ETIQUETA_MODULO,
  ETIQUETA_TIPO_TICKET,
  MOTIVO_SIN_SOPORTE,
  formato,
  opcionesModulo,
} from '@/lib/dominio'

import type { TicketResumen } from './tipos'

const ORDENES = [
  { valor: 'fechaCreacion,desc', etiqueta: 'Más recientes primero' },
  { valor: 'fechaCreacion,asc', etiqueta: 'Más antiguos primero' },
  { valor: 'fechaLimiteRespuesta,asc', etiqueta: 'Vencen primero' },
]

/** Bandeja de tickets (HU22): cada quien ve lo que su alcance le permite; el backend filtra. */
export function PaginaTickets() {
  const { usuario, puede, esPersonal } = usePermisos()
  const [pagina, setPagina] = useState(0)
  const [ahora] = useState(() => Date.now())
  const [f, setF] = useState({ estado: '', modulo: '', tipo: '', orden: 'fechaCreacion,desc', sinAsignar: false, mios: false })
  const filtrar = (cambio: Partial<typeof f>) => {
    setF((v) => ({ ...v, ...cambio }))
    setPagina(0)
  }

  const params = {
    estado: f.estado,
    modulo: f.modulo,
    tipo: f.tipo,
    orden: f.orden,
    sinAsignar: f.sinAsignar || undefined,
    agenteId: f.mios ? usuario?.id : undefined,
    pagina,
  }
  const q = useQuery({
    queryKey: ['tickets', 'lista', params],
    queryFn: ({ signal }) => api<Pagina<TicketResumen>>('/api/tickets', { params, signal }),
    placeholderData: (anterior) => anterior,
  })
  const membresia = useMembresia(null, !esPersonal && puede('MEMBRESIAS_VER'))
  const bloqueado = membresia.data && !membresia.data.soporte.habilitado ? membresia.data.soporte : null

  return (
    <div className="flex flex-col gap-6">
      <Encabezado
        titulo="Tickets"
        descripcion={esPersonal ? 'Incidentes y sugerencias de todas las empresas.' : 'Incidentes y sugerencias de tu empresa.'}
        acciones={
          puede('TICKETS_CREAR') &&
          !bloqueado && (
            <Link
              to="/tickets/nuevo"
              className="inline-flex h-10 items-center gap-2 rounded-control bg-primario px-4 text-sm font-semibold text-sobre-primario hover:bg-primario-hover"
            >
              <Plus aria-hidden className="size-4" />
              Nuevo ticket
            </Link>
          )
        }
      />

      {bloqueado && (
        <Aviso tono="peligro" titulo="Soporte bloqueado">
          No puedes crear tickets porque {MOTIVO_SIN_SOPORTE[bloqueado.motivo ?? ''] ?? 'la membresía no está vigente'}. Puedes seguir
          consultando tus tickets.
        </Aviso>
      )}

      <Filtros>
        <Selector etiqueta="Estado" vacio="Todos" value={f.estado} onChange={(e) => filtrar({ estado: e.target.value })}
          opciones={ESTADOS_TICKET.map((e) => ({ valor: e, etiqueta: ESTADO_TICKET[e].etiqueta }))} />
        <Selector etiqueta="Módulo" vacio="Todos" value={f.modulo} onChange={(e) => filtrar({ modulo: e.target.value })} opciones={opcionesModulo()} />
        <Selector etiqueta="Tipo" vacio="Todos" value={f.tipo} onChange={(e) => filtrar({ tipo: e.target.value })}
          opciones={[{ valor: 'INCIDENTE', etiqueta: 'Incidente' }, { valor: 'SUGERENCIA', etiqueta: 'Sugerencia' }]} />
        <Selector etiqueta="Orden" value={f.orden} onChange={(e) => filtrar({ orden: e.target.value })} opciones={ORDENES} />
      </Filtros>
      {esPersonal && (
        <div className="-mt-3 flex flex-wrap gap-5">
          <Casilla etiqueta="Solo sin asignar" checked={f.sinAsignar} onChange={(e) => filtrar({ sinAsignar: e.target.checked, mios: false })} />
          {puede('TICKETS_ASIGNAR') && (
            <Casilla etiqueta="Asignados a mí" checked={f.mios} onChange={(e) => filtrar({ mios: e.target.checked, sinAsignar: false })} />
          )}
        </div>
      )}

      <Tarjeta sinRelleno>
        {q.isPending ? (
          <Cargando texto="Cargando tickets…" />
        ) : q.isError ? (
          <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div>
        ) : q.data.contenido.length === 0 ? (
          <Vacio titulo="No hay tickets con esos filtros">Prueba con otro estado o módulo.</Vacio>
        ) : (
          <>
            <Tabla>
              <thead>
                <tr>
                  <Th>Ticket</Th>
                  <Th>Módulo</Th>
                  <Th>Estado</Th>
                  {puede('TICKETS_VER') && <Th>{esPersonal ? 'Empresa' : 'Solicitante'}</Th>}
                  {esPersonal && <Th>Agente</Th>}
                  <Th>Respuesta antes de</Th>
                  <Th className="text-right">Creado</Th>
                </tr>
              </thead>
              <tbody>
                {q.data.contenido.map((t) => {
                  const vencido = (t.estado === 'ABIERTO' || t.estado === 'EN_PROCESO') && new Date(t.fechaLimiteRespuesta).getTime() < ahora
                  return (
                    <Tr key={t.id}>
                      <Td className="max-w-80">
                        <Link to={`/tickets/${t.id}`} className="font-medium hover:text-primario hover:underline">
                          {t.titulo}
                        </Link>
                        <p className="text-xs text-texto-suave">{ETIQUETA_TIPO_TICKET[t.tipo]}</p>
                      </Td>
                      <Td><Insignia tono="primario" punto={COLOR_MODULO[t.modulo]}>{ETIQUETA_MODULO[t.modulo]}</Insignia></Td>
                      <Td><Insignia tono={ESTADO_TICKET[t.estado].tono}>{ESTADO_TICKET[t.estado].etiqueta}</Insignia></Td>
                      {puede('TICKETS_VER') && <Td className="text-texto-suave">{esPersonal ? t.empresaNombre : t.solicitanteNombre}</Td>}
                      {esPersonal && <Td className="text-texto-suave">{t.agenteNombre ?? <span className="italic">Sin asignar</span>}</Td>}
                      <Td className={cn('whitespace-nowrap', vencido ? 'font-semibold text-peligro-texto' : 'text-texto-suave')}>
                        {formato.fechaHora(t.fechaLimiteRespuesta)}
                        {vencido && <span className="sr-only"> (vencido)</span>}
                      </Td>
                      <Td className="text-right whitespace-nowrap text-texto-suave">{formato.fecha(t.fechaCreacion)}</Td>
                    </Tr>
                  )
                })}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
    </div>
  )
}
