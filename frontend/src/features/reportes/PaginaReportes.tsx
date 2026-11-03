import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { useSearchParams } from 'react-router'

import { api, type Pagina, type Parametros } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, CampoTexto, Cargando, Encabezado, Estadistica, Insignia, Paginador, Selector, Tabla, Tarjeta, Td, Th, Tr, Vacio } from '@/components/ui'
import { cn } from '@/lib/cn'
import {
  COLOR_MODULO, ESTADO_CAPACITACION, ESTADO_MEMBRESIA, ETIQUETA_MODULO, ETIQUETA_NIVEL, formato, opcionesModulo,
  type EstadoCapacitacion, type EstadoMembresia, type ModuloErp, type NivelAcceso,
} from '@/lib/dominio'

import { Barras, Columnas } from './Graficas'

/** Respuesta de /api/reportes/* (tarea 2.9). */
type Reporte<F> = { reporte: string; titulo: string; generadoEn: string; parametros: Record<string, unknown>; resumen: Record<string, number | string | null> | null; filas: F }

function useReporte<F>(ruta: string, params: Parametros = {}) {
  return useQuery({
    queryKey: ['reportes', ruta, params],
    queryFn: ({ signal }) => api<Reporte<F>>(`/api/reportes/${ruta}`, { params, signal }),
    placeholderData: (anterior) => anterior,
  })
}

const REPORTES = [
  { id: 'A1', nombre: 'Incidentes por módulo' },
  { id: 'A2', nombre: 'Membresías por plan' },
  { id: 'A3', nombre: 'Carga por agente' },
  { id: 'A4', nombre: 'Plan y tickets por empresa' },
  { id: 'A5', nombre: 'Recaudo mensual' },
  { id: 'A6', nombre: 'Asistencia a capacitaciones' },
  { id: 'A7', nombre: 'Base de conocimientos' },
  { id: 'A8', nombre: 'Suspendidas con tickets abiertos' },
  { id: 'A9', nombre: 'Impacto de las capacitaciones' },
] as const

/**
 * Panel de analítica (tarea 3.6): los nueve reportes de agregación A1 a A9 (fase 1.6, API 2.9). El
 * reporte elegido queda en la dirección (?r=A5) para compartirlo o recargar sin perderlo.
 */
export function PaginaReportes() {
  const [busqueda, setBusqueda] = useSearchParams()
  const actual = REPORTES.find((r) => r.id === busqueda.get('r')) ?? REPORTES[0]
  const PANELES: Record<(typeof REPORTES)[number]['id'], () => ReactNode> = {
    A1: () => <A1 />, A2: () => <A2 />, A3: () => <A3 />, A4: () => <A4 />, A5: () => <A5 />, A6: () => <A6 />, A7: () => <A7 />, A8: () => <A8 />, A9: () => <A9 />,
  }
  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Reportes" descripcion="Indicadores calculados en MongoDB con pipelines de agregación." />
      <div className="grid gap-6 lg:grid-cols-[15rem_1fr]">
        <nav aria-label="Reportes" className="flex gap-1 overflow-x-auto lg:flex-col">
          {REPORTES.map((r) => (
            <button
              key={r.id}
              type="button"
              aria-current={r.id === actual.id ? 'page' : undefined}
              onClick={() => setBusqueda({ r: r.id }, { replace: true })}
              className={cn(
                'flex shrink-0 cursor-pointer items-center gap-2 rounded-control px-3 py-2 text-left text-sm whitespace-nowrap lg:whitespace-normal',
                r.id === actual.id ? 'bg-primario-suave font-semibold text-sobre-primario-suave' : 'text-texto-suave hover:bg-superficie-2 hover:text-texto',
              )}
            >
              <span className="font-mono text-xs opacity-70">{r.id}</span>
              {r.nombre}
            </button>
          ))}
        </nav>
        <div className="flex min-w-0 flex-col gap-6">{PANELES[actual.id]()}</div>
      </div>
    </div>
  )
}

/** Cabecera común: título del reporte, hora de cálculo, estados de carga y error. */
function Marco<F>({ q, filtros, children }: { q: ReturnType<typeof useReporte<F>>; filtros?: ReactNode; children: (r: Reporte<F>) => ReactNode }) {
  return (
    <>
      {filtros && <div className="grid gap-3 sm:grid-cols-3">{filtros}</div>}
      {q.isPending ? <Cargando texto="Calculando…" /> : q.isError ? <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso> : (
        <>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-semibold">{q.data.reporte} · {q.data.titulo}</h2>
            <p className="text-xs text-texto-suave">Calculado el {formato.fechaHora(q.data.generadoEn)}</p>
          </div>
          {children(q.data)}
        </>
      )}
    </>
  )
}

const n = (v: unknown) => (typeof v === 'number' ? formato.numero(v) : '—')
const pct = (v: number | null | undefined) => (v == null ? '—' : `${v.toLocaleString('es-CO', { maximumFractionDigits: 1 })} %`)
const Mod = ({ m }: { m: string }) => (
  <Insignia tono="primario" punto={COLOR_MODULO[m as ModuloErp]}>{ETIQUETA_MODULO[m as ModuloErp] ?? m}</Insignia>
)

// A1 ------------------------------------------------------------------------------------------
type F1 = { modulo: ModuloErp; incidentes: number; abiertos: number; cerrados: number; horasPromedioResolucion: number | null; porcentajeCerrados: number }
function A1() {
  const [p, setP] = useState({ desde: '', hasta: '' })
  const q = useReporte<F1[]>('incidentes-por-modulo', p)
  return (
    <Marco q={q} filtros={<>
      <CampoTexto etiqueta="Desde" type="date" value={p.desde} onChange={(e) => setP((v) => ({ ...v, desde: e.target.value }))} />
      <CampoTexto etiqueta="Hasta" type="date" value={p.hasta} onChange={(e) => setP((v) => ({ ...v, hasta: e.target.value }))} />
    </>}>
      {(r) => (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Estadistica etiqueta="Incidentes" valor={n(r.resumen?.totalIncidentes)} detalle={p.desde || p.hasta ? 'En el rango elegido' : 'Todo el historial'} />
            <Estadistica etiqueta="Módulo con más incidentes" valor={r.filas[0] ? ETIQUETA_MODULO[r.filas[0].modulo] : '—'} />
          </div>
          <Tarjeta titulo="Incidentes por módulo">
            <Barras titulo="Incidentes por módulo" datos={r.filas.map((f) => ({ clave: f.modulo, etiqueta: ETIQUETA_MODULO[f.modulo], valor: f.incidentes, texto: formato.numero(f.incidentes), color: COLOR_MODULO[f.modulo] }))} />
          </Tarjeta>
          <Tarjeta sinRelleno>
            <Tabla>
              <thead><tr><Th>Módulo</Th><Th className="text-right">Incidentes</Th><Th className="text-right">Abiertos</Th><Th className="text-right">Cerrados</Th><Th className="text-right">% cerrados</Th><Th className="text-right">Horas para resolver</Th></tr></thead>
              <tbody>{r.filas.map((f) => (
                <Tr key={f.modulo}><Td><Mod m={f.modulo} /></Td><Td className="text-right tabular-nums">{n(f.incidentes)}</Td><Td className="text-right tabular-nums">{n(f.abiertos)}</Td>
                  <Td className="text-right tabular-nums">{n(f.cerrados)}</Td><Td className="text-right tabular-nums">{pct(f.porcentajeCerrados)}</Td>
                  <Td className="text-right tabular-nums">{f.horasPromedioResolucion == null ? '—' : f.horasPromedioResolucion.toLocaleString('es-CO', { maximumFractionDigits: 1 })}</Td></Tr>
              ))}</tbody>
            </Tabla>
          </Tarjeta>
        </>
      )}
    </Marco>
  )
}

// A2 ------------------------------------------------------------------------------------------
type F2 = { planId: string; plan: string; nivelAcceso: NivelAcceso; precio: number; activo: boolean; empresasActivas: number; vigentes: number; vencidas: number; empresas: string[] }
function A2() {
  const q = useReporte<F2[]>('membresias-activas-por-plan')
  return (
    <Marco q={q}>
      {(r) => (
        <>
          <Tarjeta titulo="Empresas con membresía activa">
            <Barras titulo="Empresas con membresía activa por plan" datos={r.filas.map((f) => ({ clave: f.planId, etiqueta: f.plan, valor: f.empresasActivas, texto: formato.numero(f.empresasActivas), detalle: f.empresas.join(', ') || 'Ninguna' }))} />
          </Tarjeta>
          <Tarjeta sinRelleno>
            <Tabla>
              <thead><tr><Th>Plan</Th><Th>Nivel</Th><Th className="text-right">Precio</Th><Th className="text-right">Activas</Th><Th className="text-right">Vigentes</Th><Th className="text-right">Vencidas</Th></tr></thead>
              <tbody>{r.filas.map((f) => (
                <Tr key={f.planId}><Td className="font-medium">{f.plan} {!f.activo && <Insignia>Inactivo</Insignia>}</Td><Td>{ETIQUETA_NIVEL[f.nivelAcceso] ?? f.nivelAcceso}</Td>
                  <Td className="text-right tabular-nums">{formato.pesos(f.precio)}</Td><Td className="text-right tabular-nums">{n(f.empresasActivas)}</Td>
                  <Td className="text-right tabular-nums">{n(f.vigentes)}</Td><Td className={cn('text-right tabular-nums', f.vencidas > 0 && 'font-semibold text-aviso-texto')}>{n(f.vencidas)}</Td></Tr>
              ))}</tbody>
            </Tabla>
          </Tarjeta>
        </>
      )}
    </Marco>
  )
}

// A3 ------------------------------------------------------------------------------------------
type F3 = { usuarioId: string; nombre: string; email: string; activo: boolean; abiertos: number; enProceso: number; masAntiguo: string | null }
function A3() {
  const q = useReporte<F3[]>('carga-por-agente')
  return (
    <Marco q={q}>
      {(r) => (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Estadistica etiqueta="Agentes" valor={n(r.resumen?.agentes)} />
            <Estadistica etiqueta="Abiertos asignados" valor={n(r.resumen?.abiertosAsignados)} />
            <Estadistica etiqueta="Abiertos sin asignar" valor={n(r.resumen?.abiertosSinAsignar)} detalle="Esperan un agente" />
          </div>
          <Tarjeta titulo="Tickets abiertos por agente">
            {r.filas.length === 0 ? <Vacio titulo="No hay agentes" /> : (
              <Barras titulo="Tickets abiertos por agente" datos={r.filas.map((f) => ({ clave: f.usuarioId, etiqueta: f.nombre, valor: f.abiertos, texto: formato.numero(f.abiertos), detalle: `${f.nombre}: ${f.abiertos} abiertos, ${f.enProceso} en proceso` }))} />
            )}
          </Tarjeta>
          <Tarjeta sinRelleno>
            <Tabla>
              <thead><tr><Th>Agente</Th><Th className="text-right">Abiertos</Th><Th className="text-right">En proceso</Th><Th>Más antiguo</Th></tr></thead>
              <tbody>{r.filas.map((f) => (
                <Tr key={f.usuarioId}><Td><p className="font-medium">{f.nombre} {!f.activo && <Insignia>Inactivo</Insignia>}</p><p className="text-xs text-texto-suave">{f.email}</p></Td>
                  <Td className="text-right tabular-nums">{n(f.abiertos)}</Td><Td className="text-right tabular-nums">{n(f.enProceso)}</Td>
                  <Td className="whitespace-nowrap text-texto-suave">{f.masAntiguo ? formato.fecha(f.masAntiguo) : '—'}</Td></Tr>
              ))}</tbody>
            </Tabla>
          </Tarjeta>
        </>
      )}
    </Marco>
  )
}

// A4 ------------------------------------------------------------------------------------------
type F4 = { empresaId: string; nit: string; razonSocial: string; plan: string; estadoMembresia: string; modulos: number; tickets: number; abiertos: number; incidentes: number; sugerencias: number }
function A4() {
  const [pagina, setPagina] = useState(0)
  const q = useReporte<Pagina<F4>>('plan-y-tickets-por-empresa', { pagina })
  return (
    <Marco q={q}>
      {(r) => (
        <Tarjeta sinRelleno>
          <Tabla>
            <thead><tr><Th>Empresa</Th><Th>Plan</Th><Th>Membresía</Th><Th className="text-right">Módulos</Th><Th className="text-right">Tickets</Th><Th className="text-right">Abiertos</Th><Th className="text-right">Incidentes</Th><Th className="text-right">Sugerencias</Th></tr></thead>
            <tbody>{r.filas.contenido.map((f) => {
              const e = ESTADO_MEMBRESIA[f.estadoMembresia as EstadoMembresia]
              return (
                <Tr key={f.empresaId}><Td><p className="font-medium">{f.razonSocial}</p><p className="font-mono text-xs text-texto-suave">{f.nit}</p></Td><Td>{f.plan}</Td>
                  <Td>{e ? <Insignia tono={e.tono}>{e.etiqueta}</Insignia> : '—'}</Td><Td className="text-right tabular-nums">{n(f.modulos)}</Td>
                  <Td className="text-right tabular-nums">{n(f.tickets)}</Td><Td className="text-right tabular-nums">{n(f.abiertos)}</Td>
                  <Td className="text-right tabular-nums">{n(f.incidentes)}</Td><Td className="text-right tabular-nums">{n(f.sugerencias)}</Td></Tr>
              )
            })}</tbody>
          </Tabla>
          <Paginador pagina={r.filas.pagina} totalPaginas={r.filas.totalPaginas} totalElementos={r.filas.totalElementos} onCambiar={setPagina} />
        </Tarjeta>
      )}
    </Marco>
  )
}

// A5 ------------------------------------------------------------------------------------------
type F5 = { mes: string; recaudo: number; aprobados: number; fallidos: number; montoFallido: number; porcentajeFallidos: number }
const nombreMes = (mes: string) => new Intl.DateTimeFormat('es-CO', { month: 'short', year: '2-digit', timeZone: 'UTC' }).format(new Date(`${mes}-15T12:00:00Z`))
function A5() {
  const [p, setP] = useState({ desde: '', hasta: '' })
  const q = useReporte<F5[]>('recaudo-mensual', p)
  return (
    <Marco q={q} filtros={<>
      <CampoTexto etiqueta="Desde (mes)" type="month" value={p.desde} onChange={(e) => setP((v) => ({ ...v, desde: e.target.value }))} ayuda="Vacío: los últimos 12 meses." />
      <CampoTexto etiqueta="Hasta (mes)" type="month" value={p.hasta} onChange={(e) => setP((v) => ({ ...v, hasta: e.target.value }))} />
    </>}>
      {(r) => (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Estadistica etiqueta="Recaudo" valor={formato.pesos(Number(r.resumen?.recaudoTotal ?? 0))} detalle={`${n(r.resumen?.pagosAprobados)} pagos aprobados`} />
            <Estadistica etiqueta="Pagos fallidos" valor={n(r.resumen?.pagosFallidos)} detalle={`${pct(r.resumen?.porcentajeFallidos as number | null)} del total`} />
          </div>
          <Tarjeta titulo="Recaudo por mes" descripcion="Suma de los pagos aprobados.">
            {r.filas.length === 0 ? <Vacio titulo="Sin pagos en el rango" /> : (
              <Columnas titulo="Recaudo por mes" datos={r.filas.map((f) => ({ clave: f.mes, etiqueta: nombreMes(f.mes), valor: Number(f.recaudo), texto: formato.pesos(f.recaudo), detalle: `${nombreMes(f.mes)}: ${formato.pesos(f.recaudo)} · ${f.fallidos} fallidos` }))} />
            )}
          </Tarjeta>
          <Tarjeta sinRelleno>
            <Tabla>
              <thead><tr><Th>Mes</Th><Th className="text-right">Recaudo</Th><Th className="text-right">Aprobados</Th><Th className="text-right">Fallidos</Th><Th className="text-right">Monto fallido</Th><Th className="text-right">% fallidos</Th></tr></thead>
              <tbody>{r.filas.map((f) => (
                <Tr key={f.mes}><Td className="capitalize">{nombreMes(f.mes)}</Td><Td className="text-right tabular-nums">{formato.pesos(f.recaudo)}</Td>
                  <Td className="text-right tabular-nums">{n(f.aprobados)}</Td><Td className="text-right tabular-nums">{n(f.fallidos)}</Td>
                  <Td className="text-right tabular-nums">{formato.pesos(f.montoFallido)}</Td><Td className="text-right tabular-nums">{pct(f.porcentajeFallidos)}</Td></Tr>
              ))}</tbody>
            </Tabla>
          </Tarjeta>
        </>
      )}
    </Marco>
  )
}

// A6 ------------------------------------------------------------------------------------------
type F6 = { capacitacionId: string; titulo: string; tipo: string; modulo: ModuloErp; fechaInicio: string; estado: string; realizada: boolean; cupo: number; cuposOcupados: number; inscritos: number; asistieron: number; marcadas: number; porcentajeAsistencia: number | null }
function A6() {
  const [p, setP] = useState({ desde: '', hasta: '', modulo: '' })
  const [pagina, setPagina] = useState(0)
  const q = useReporte<Pagina<F6>>('asistencia-por-capacitacion', { ...p, pagina })
  const cambiar = (c: Partial<typeof p>) => { setP((v) => ({ ...v, ...c })); setPagina(0) }
  return (
    <Marco q={q} filtros={<>
      <CampoTexto etiqueta="Desde" type="date" value={p.desde} onChange={(e) => cambiar({ desde: e.target.value })} />
      <CampoTexto etiqueta="Hasta" type="date" value={p.hasta} onChange={(e) => cambiar({ hasta: e.target.value })} />
      <Selector etiqueta="Módulo" vacio="Todos" value={p.modulo} onChange={(e) => cambiar({ modulo: e.target.value })} opciones={opcionesModulo()} />
    </>}>
      {(r) => (
        <Tarjeta sinRelleno>
          {r.filas.contenido.length === 0 ? <Vacio titulo="Sin capacitaciones" /> : (
            <Tabla>
              <thead><tr><Th>Capacitación</Th><Th>Módulo</Th><Th>Estado</Th><Th className="text-right">Inscritos</Th><Th className="text-right">Asistieron</Th><Th>Asistencia</Th></tr></thead>
              <tbody>{r.filas.contenido.map((f) => {
                const est: EstadoCapacitacion = f.estado === 'CANCELADA' ? 'CANCELADA' : f.realizada ? 'REALIZADA' : 'PROGRAMADA'
                return (
                  <Tr key={f.capacitacionId}>
                    <Td><p className="font-medium">{f.titulo}</p><p className="text-xs text-texto-suave">{formato.fechaHora(f.fechaInicio)} · {f.tipo === 'MASIVA' ? 'Masiva' : 'Personalizada'}</p></Td>
                    <Td><Mod m={f.modulo} /></Td><Td><Insignia tono={ESTADO_CAPACITACION[est].tono}>{ESTADO_CAPACITACION[est].etiqueta}</Insignia></Td>
                    <Td className="text-right tabular-nums">{n(f.inscritos)}</Td><Td className="text-right tabular-nums">{n(f.asistieron)} / {n(f.marcadas)}</Td>
                    <Td className="min-w-36">
                      {f.porcentajeAsistencia == null ? <span className="text-texto-suave">Sin marcas</span> : (
                        <div className="flex items-center gap-2">
                          <span className="h-2 flex-1 rounded-full bg-superficie-2" aria-hidden><span className="block h-full rounded-full bg-primario" style={{ width: `${f.porcentajeAsistencia}%` }} /></span>
                          <span className="w-12 text-right tabular-nums">{pct(f.porcentajeAsistencia)}</span>
                        </div>
                      )}
                    </Td>
                  </Tr>
                )
              })}</tbody>
            </Tabla>
          )}
          <Paginador pagina={r.filas.pagina} totalPaginas={r.filas.totalPaginas} totalElementos={r.filas.totalElementos} onCambiar={setPagina} />
        </Tarjeta>
      )}
    </Marco>
  )
}

// A7 ------------------------------------------------------------------------------------------
type F7 = { modulo: ModuloErp; articulos: number; publicos: number; internos: number; conVector: number; ticketsVinculados: number; ticketsResueltos: number; resueltosPorArticulo: number | null }
function A7() {
  const q = useReporte<F7[]>('articulos-por-modulo')
  return (
    <Marco q={q}>
      {(r) => (
        <>
          <Tarjeta titulo="Tickets resueltos con un artículo">
            <Barras titulo="Tickets resueltos por módulo" datos={r.filas.map((f) => ({ clave: f.modulo, etiqueta: ETIQUETA_MODULO[f.modulo], valor: f.ticketsResueltos, texto: formato.numero(f.ticketsResueltos), color: COLOR_MODULO[f.modulo] }))} />
          </Tarjeta>
          <Tarjeta sinRelleno>
            <Tabla>
              <thead><tr><Th>Módulo</Th><Th className="text-right">Artículos</Th><Th className="text-right">Públicos / internos</Th><Th className="text-right">Con vector</Th><Th className="text-right">Tickets vinculados</Th><Th className="text-right">Resueltos</Th><Th className="text-right">Por artículo</Th></tr></thead>
              <tbody>{r.filas.map((f) => (
                <Tr key={f.modulo}><Td><Mod m={f.modulo} /></Td><Td className="text-right tabular-nums">{n(f.articulos)}</Td><Td className="text-right tabular-nums">{n(f.publicos)} / {n(f.internos)}</Td>
                  <Td className="text-right tabular-nums">{n(f.conVector)}</Td><Td className="text-right tabular-nums">{n(f.ticketsVinculados)}</Td><Td className="text-right tabular-nums">{n(f.ticketsResueltos)}</Td>
                  <Td className="text-right tabular-nums">{f.resueltosPorArticulo == null ? '—' : f.resueltosPorArticulo.toLocaleString('es-CO', { maximumFractionDigits: 1 })}</Td></Tr>
              ))}</tbody>
            </Tabla>
          </Tarjeta>
        </>
      )}
    </Marco>
  )
}

// A8 ------------------------------------------------------------------------------------------
type F8 = { empresaId: string; empresa: string; nit: string; contacto: string; telefono: string; email: string; plan: string; suspendidaDesde: string; ticketsAbiertos: number; abiertoMasAntiguo: string }
function A8() {
  const q = useReporte<F8[]>('suspendidas-con-tickets-abiertos')
  return (
    <Marco q={q}>
      {(r) => r.filas.length === 0 ? <Tarjeta><Vacio titulo="Ninguna empresa suspendida tiene tickets abiertos" /></Tarjeta> : (
        <Tarjeta sinRelleno descripcion="Para llamarlas: tienen tickets abiertos pero el soporte está bloqueado." titulo={`${r.filas.length} empresas`}>
          <Tabla>
            <thead><tr><Th>Empresa</Th><Th>Contacto</Th><Th>Plan</Th><Th>Suspendida desde</Th><Th className="text-right">Abiertos</Th><Th>Más antiguo</Th></tr></thead>
            <tbody>{r.filas.map((f) => (
              <Tr key={f.empresaId}><Td><p className="font-medium">{f.empresa}</p><p className="font-mono text-xs text-texto-suave">{f.nit}</p></Td>
                <Td><p>{f.contacto}</p><p className="text-xs text-texto-suave">{f.telefono} · {f.email}</p></Td><Td>{f.plan}</Td>
                <Td className="whitespace-nowrap">{formato.fecha(f.suspendidaDesde)}</Td><Td className="text-right font-semibold tabular-nums">{n(f.ticketsAbiertos)}</Td>
                <Td className="whitespace-nowrap text-texto-suave">{formato.fecha(f.abiertoMasAntiguo)}</Td></Tr>
            ))}</tbody>
          </Tabla>
        </Tarjeta>
      )}
    </Marco>
  )
}

// A9 ------------------------------------------------------------------------------------------
type F9 = { modulo: ModuloErp; asistencias: number; antes: number; despues: number; variacionPorcentual: number | null }
function A9() {
  const [dias, setDias] = useState('90')
  const q = useReporte<F9[]>('impacto-capacitaciones', { dias })
  return (
    <Marco q={q} filtros={
      <Selector etiqueta="Ventana antes y después" value={dias} onChange={(e) => setDias(e.target.value)}
        opciones={['30', '60', '90', '120', '180'].map((d) => ({ valor: d, etiqueta: `${d} días` }))} />
    }>
      {(r) => (
        <Tarjeta sinRelleno descripcion={`Incidentes del mismo usuario y módulo ${dias} días antes y después de asistir. ${n(r.resumen?.asistencias)} asistencias analizadas.`} titulo="¿Bajan los incidentes?">
          <Tabla>
            <thead><tr><Th>Módulo</Th><Th className="text-right">Asistencias</Th><Th className="text-right">Antes</Th><Th className="text-right">Después</Th><Th>Variación</Th></tr></thead>
            <tbody>{r.filas.map((f) => <FilaA9 key={f.modulo} f={f} />)}</tbody>
          </Tabla>
        </Tarjeta>
      )}
    </Marco>
  )
}

function FilaA9({ f }: { f: F9 }) {
  const v = f.variacionPorcentual
  const Icono = v == null || v === 0 ? Minus : v < 0 ? ArrowDownRight : ArrowUpRight
  return (
    <Tr>
      <Td><Mod m={f.modulo} /></Td><Td className="text-right tabular-nums">{n(f.asistencias)}</Td>
      <Td className="text-right tabular-nums">{n(f.antes)}</Td><Td className="text-right tabular-nums">{n(f.despues)}</Td>
      <Td>
        <Insignia tono={v == null || v === 0 ? 'neutro' : v < 0 ? 'exito' : 'peligro'}>
          <Icono aria-hidden className="size-3.5" />
          {v == null ? 'Sin incidentes antes' : `${v > 0 ? '+' : ''}${pct(v)}`}
          <span className="sr-only">{v != null && (v < 0 ? ' (bajaron)' : v > 0 ? ' (subieron)' : '')}</span>
        </Insignia>
      </Td>
    </Tr>
  )
}
