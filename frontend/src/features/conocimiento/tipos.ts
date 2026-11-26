import type { ModuloErp, Visibilidad } from '@/lib/dominio'

/** Respuestas de /api/articulos (tarea 2.7). */
export type ArticuloResumen = {
  id: string
  titulo: string
  modulo: ModuloErp
  etiquetas: string[]
  visibilidad: Visibilidad
  autorNombre: string
  ticketsVinculados: number
  vectorPendiente: boolean
  fechaCreacion: string
  fechaActualizacion: string
}

/** ticketIds solo llega para el personal (alcance GLOBAL con TICKETS_VER); para los demás es null (acta 2.7). */
export type Articulo = ArticuloResumen & { contenido: string; autorId: string; ticketIds: string[] | null }

export type ResultadoBusqueda = {
  origen: 'TEXTO' | 'SEMANTICA'
  aviso: string | null
  resultados: { id: string; titulo: string; modulo: ModuloErp; etiquetas: string[]; visibilidad: Visibilidad; extracto: string; puntaje: number }[]
}
