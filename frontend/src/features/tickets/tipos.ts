import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/cliente'
import { numero } from '@/lib/formulario'
import type { EstadoTicket, ModuloErp, TipoTicket } from '@/lib/dominio'

/** Respuestas de /api/tickets y /api/comentarios (tarea 2.6). */
export type TicketResumen = {
  id: string
  empresaId: string
  empresaNombre: string
  solicitanteId: string
  solicitanteNombre: string
  agenteId: string | null
  agenteNombre: string | null
  modulo: ModuloErp
  tipo: TipoTicket
  titulo: string
  estado: EstadoTicket
  fechaLimiteRespuesta: string
  fechaResolucion: string | null
  fechaCreacion: string
}

export type Ticket = TicketResumen & {
  creadorId: string
  creadorNombre: string
  descripcion: string
  detalle: Record<string, string | number> | null
  historialEstados: { estado: EstadoTicket; fecha: string; usuarioId: string; usuarioNombre: string }[]
  fechaPrimeraAsignacion: string | null
  fechaActualizacion: string
}

export type Comentario = {
  id: string
  ticketId: string
  autorId: string
  autorNombre: string
  texto: string
  editado: boolean
  fechaCreacion: string
  fechaActualizacion: string
}

export type TipoCampo = 'TEXTO' | 'TEXTO_LARGO' | 'ENTERO' | 'DECIMAL' | 'DINERO' | 'OPCION'
export type CampoModulo = {
  nombre: string
  etiqueta: string
  tipo: TipoCampo
  obligatorio: boolean
  longitudMaxima: number | null
  opciones: { valor: string; etiqueta: string }[] | null
}
export type CamposModulo = { modulo: ModuloErp; etiqueta: string; campos: CampoModulo[] }

/** Catálogo de campos de cada módulo (GET /api/tickets/campos): no cambia mientras corre la app. */
export function useCamposModulo() {
  return useQuery({
    queryKey: ['tickets', 'campos'],
    queryFn: ({ signal }) => api<CamposModulo[]>('/api/tickets/campos', { signal }),
    staleTime: Infinity,
  })
}

/** Valores del formulario (texto) → detalle del JSON: números como número y sin los vacíos. */
export function detalleParaEnviar(campos: CampoModulo[], valores: Record<string, string>) {
  const detalle: Record<string, string | number> = {}
  for (const c of campos) {
    const v = (valores[c.nombre] ?? '').trim()
    if (v === '') continue
    if (c.tipo === 'ENTERO' || c.tipo === 'DECIMAL' || c.tipo === 'DINERO') {
      const n = numero(v)
      detalle[c.nombre] = n ?? v // un texto no numérico llega al backend, que responde el error del campo
    } else {
      detalle[c.nombre] = v
    }
  }
  return detalle
}

/** detalle guardado → valores de texto del formulario. */
export function detalleParaFormulario(detalle: Ticket['detalle']): Record<string, string> {
  return Object.fromEntries(Object.entries(detalle ?? {}).map(([k, v]) => [k, String(v)]))
}
