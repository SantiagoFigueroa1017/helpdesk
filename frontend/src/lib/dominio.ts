/**
 * Valores fijos del dominio con su etiqueta en español. Los nombres son los mismos enums del backend
 * (contexto maestro, sección 3 y modelo 1.2), así el frontend envía y recibe exactamente esos textos.
 */

export const MODULOS_ERP = [
  'VENTAS_ONLINE',
  'INVENTARIOS',
  'COMPRAS',
  'PRODUCCION',
  'VENTAS_EN_SITIO',
] as const
export type ModuloErp = (typeof MODULOS_ERP)[number]

export const ETIQUETA_MODULO: Record<ModuloErp, string> = {
  VENTAS_ONLINE: 'Ventas Online',
  INVENTARIOS: 'Inventarios',
  COMPRAS: 'Compras',
  PRODUCCION: 'Producción',
  VENTAS_EN_SITIO: 'Ventas en Sitio (Caja/POS)',
}

/** Clase de color de cada módulo (tokens --color-mod-*), para chips y gráficas. */
export const COLOR_MODULO: Record<ModuloErp, string> = {
  VENTAS_ONLINE: 'bg-mod-ventas-online',
  INVENTARIOS: 'bg-mod-inventarios',
  COMPRAS: 'bg-mod-compras',
  PRODUCCION: 'bg-mod-produccion',
  VENTAS_EN_SITIO: 'bg-mod-ventas-sitio',
}

export type Tono = 'neutro' | 'primario' | 'exito' | 'aviso' | 'peligro' | 'info'

export const ESTADOS_TICKET = ['ABIERTO', 'EN_PROCESO', 'RESUELTO', 'CERRADO'] as const
export type EstadoTicket = (typeof ESTADOS_TICKET)[number]

export const ESTADO_TICKET: Record<EstadoTicket, { etiqueta: string; tono: Tono }> = {
  ABIERTO: { etiqueta: 'Abierto', tono: 'aviso' },
  EN_PROCESO: { etiqueta: 'En proceso', tono: 'info' },
  RESUELTO: { etiqueta: 'Resuelto', tono: 'exito' },
  CERRADO: { etiqueta: 'Cerrado', tono: 'neutro' },
}

export type TipoTicket = 'INCIDENTE' | 'SUGERENCIA'
export const ETIQUETA_TIPO_TICKET: Record<TipoTicket, string> = {
  INCIDENTE: 'Incidente',
  SUGERENCIA: 'Sugerencia',
}

/** estadoVisible de la membresía (2.5): VENCIDA se deriva, no se guarda. */
export type EstadoMembresia = 'PENDIENTE' | 'ACTIVA' | 'SUSPENDIDA' | 'VENCIDA'
export const ESTADO_MEMBRESIA: Record<EstadoMembresia, { etiqueta: string; tono: Tono }> = {
  PENDIENTE: { etiqueta: 'Pendiente', tono: 'neutro' },
  ACTIVA: { etiqueta: 'Activa', tono: 'exito' },
  SUSPENDIDA: { etiqueta: 'Suspendida', tono: 'peligro' },
  VENCIDA: { etiqueta: 'Vencida', tono: 'aviso' },
}

/** estadoVisible de una capacitación (2.8). */
export type EstadoCapacitacion = 'PROGRAMADA' | 'EN_CURSO' | 'REALIZADA' | 'CANCELADA'
export const ESTADO_CAPACITACION: Record<EstadoCapacitacion, { etiqueta: string; tono: Tono }> = {
  PROGRAMADA: { etiqueta: 'Programada', tono: 'primario' },
  EN_CURSO: { etiqueta: 'En curso', tono: 'info' },
  REALIZADA: { etiqueta: 'Realizada', tono: 'exito' },
  CANCELADA: { etiqueta: 'Cancelada', tono: 'neutro' },
}

const formatoNumero = new Intl.NumberFormat('es-CO')
const formatoPesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
const formatoFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'America/Bogota' })
const formatoFechaHora = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Bogota',
})

export const formato = {
  numero: (n: number) => formatoNumero.format(n),
  /** El backend envía el dinero como número decimal (BigDecimal). */
  pesos: (n: number | string) => formatoPesos.format(Number(n)),
  /** Las fechas llegan en ISO-8601 UTC y se muestran en hora de Bogotá. */
  fecha: (iso: string) => formatoFecha.format(new Date(iso)),
  fechaHora: (iso: string) => formatoFechaHora.format(new Date(iso)),
}

/*
 * Fechas de los formularios. Colombia no tiene horario de verano: Bogotá es siempre UTC-5. Los
 * campos datetime-local y date muestran la hora de Bogotá y el backend recibe un Instant ISO UTC.
 */
const BOGOTA_MS = 5 * 60 * 60 * 1000

/** "2026-11-10T09:00" (hora de Bogotá) → ISO UTC. */
export const desdeFechaHoraLocal = (v: string) => (v ? new Date(`${v}:00-05:00`).toISOString() : null)
/** "2026-11-10" → medianoche de Bogotá en ISO UTC. */
export const desdeFechaLocal = (v: string) => (v ? new Date(`${v}T00:00:00-05:00`).toISOString() : null)
/** ISO UTC → "2026-11-10T09:00" para un datetime-local. */
export const aFechaHoraLocal = (iso: string | null | undefined) =>
  iso ? new Date(new Date(iso).getTime() - BOGOTA_MS).toISOString().slice(0, 16) : ''
/** ISO UTC → "2026-11-10" para un date. */
export const aFechaLocal = (iso: string | null | undefined) => aFechaHoraLocal(iso).slice(0, 10)
/** Hoy en Bogotá, "AAAA-MM-DD". */
export const hoyLocal = () => aFechaLocal(new Date().toISOString())

export const opcionesModulo = (modulos: readonly ModuloErp[] = MODULOS_ERP) =>
  modulos.map((m) => ({ valor: m, etiqueta: ETIQUETA_MODULO[m] }))

export type Visibilidad = 'PUBLICO' | 'INTERNO'
export type NivelAcceso = 'BASICO' | 'ESTANDAR' | 'PREMIUM'
export const ETIQUETA_NIVEL: Record<NivelAcceso, string> = { BASICO: 'Básico', ESTANDAR: 'Estándar', PREMIUM: 'Premium' }
export type MetodoPago = 'TRANSFERENCIA' | 'TARJETA' | 'PSE' | 'EFECTIVO' | 'CONSIGNACION'
export const ETIQUETA_METODO: Record<MetodoPago, string> = {
  TRANSFERENCIA: 'Transferencia',
  TARJETA: 'Tarjeta',
  PSE: 'PSE',
  EFECTIVO: 'Efectivo',
  CONSIGNACION: 'Consignación',
}
export type EstadoSolicitud = 'PENDIENTE' | 'PROGRAMADA' | 'RECHAZADA'
export const ESTADO_SOLICITUD: Record<EstadoSolicitud, { etiqueta: string; tono: Tono }> = {
  PENDIENTE: { etiqueta: 'Pendiente', tono: 'aviso' },
  PROGRAMADA: { etiqueta: 'Programada', tono: 'exito' },
  RECHAZADA: { etiqueta: 'Rechazada', tono: 'neutro' },
}
/** motivo de SOPORTE_BLOQUEADO (2.5). */
export const MOTIVO_SIN_SOPORTE: Record<string, string> = {
  SIN_MEMBRESIA: 'tu empresa no tiene membresía',
  PENDIENTE: 'la membresía de tu empresa está pendiente de activación',
  SUSPENDIDA: 'la membresía de tu empresa está suspendida',
  VENCIDA: 'la membresía de tu empresa está vencida',
}

/** Día sin hora ("AAAA-MM-DD", LocalDate del backend): se muestra tal cual, sin cambio de zona. */
export const formatoDia = (ymd: string) => formatoFecha.format(new Date(`${ymd}T12:00:00Z`))
