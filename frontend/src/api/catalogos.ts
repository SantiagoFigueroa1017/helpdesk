import { useQuery } from '@tanstack/react-query'

import type { EstadoMembresia, ModuloErp, NivelAcceso } from '@/lib/dominio'

import { api, type Pagina } from './cliente'

/**
 * Tipos de las respuestas del backend (fase 2) que usan varias pantallas, y consultas de catálogos
 * pequeños para mostrar nombres en lugar de ids: hay 40 empresas y 4 planes en la semilla, y el
 * tamaño máximo de página es 100 (contexto 8.2).
 */
export type Contacto = { nombre: string; email: string; telefono: string; direccion: string | null; ciudad: string | null }
export type Empresa = { id: string; nit: string; razonSocial: string; contacto: Contacto; fechaCreacion: string; fechaActualizacion: string }

export type Plan = {
  id: string
  nombre: string
  descripcion: string | null
  precio: number
  nivelAcceso: NivelAcceso
  limites: { horasRespuesta: number; capacitacionesIncluidas: number; maxUsuarios: number; maxModulos: number }
  activo: boolean
}

export type Membresia = {
  id: string
  empresaId: string
  planId: string
  modulosContratados: ModuloErp[]
  fechaInicio: string
  fechaVencimiento: string
  estado: 'PENDIENTE' | 'ACTIVA' | 'SUSPENDIDA'
  estadoVisible: EstadoMembresia
  soporte: { habilitado: boolean; motivo: string | null }
}

export type Usuario = {
  id: string
  nombre: string
  email: string
  empresaId: string | null
  roles: { id: string; nombre: string; alcance: string; activo: boolean }[]
  activo: boolean
  fechaCreacion: string
}

/** id → razón social de todas las empresas. Solo para el personal (alcance GLOBAL). */
export function useNombresEmpresas(habilitado: boolean) {
  const q = useQuery({
    queryKey: ['empresas', 'nombres'],
    queryFn: ({ signal }) => api<Pagina<Empresa>>('/api/empresas', { params: { tamano: 100, orden: 'razonSocial' }, signal }),
    enabled: habilitado,
    staleTime: 5 * 60_000,
  })
  const empresas = q.data?.contenido ?? []
  return {
    empresas,
    nombre: (id: string | null | undefined) => (id ? (empresas.find((e) => e.id === id)?.razonSocial ?? '—') : '—'),
    opciones: empresas.map((e) => ({ valor: e.id, etiqueta: e.razonSocial })),
  }
}

/** Todos los planes (PLANES_VER). */
export function usePlanes(habilitado: boolean) {
  const q = useQuery({
    queryKey: ['planes', 'todos'],
    queryFn: ({ signal }) => api<Pagina<Plan>>('/api/planes', { params: { tamano: 100, orden: 'nombre' }, signal }),
    enabled: habilitado,
    staleTime: 5 * 60_000,
  })
  const planes = q.data?.contenido ?? []
  return {
    planes,
    nombre: (id: string | null | undefined) => planes.find((p) => p.id === id)?.nombre ?? '—',
  }
}

/** Membresía de una empresa (la más reciente). Para un usuario de empresa, la suya sin enviar empresaId. */
export function useMembresia(empresaId: string | null | undefined, habilitado = true) {
  return useQuery({
    queryKey: ['membresias', 'de', empresaId ?? 'propia'],
    queryFn: async ({ signal }) => {
      const p = await api<Pagina<Membresia>>('/api/membresias', { params: { empresaId, tamano: 1 }, signal })
      return p.contenido[0] ?? null
    },
    enabled: habilitado,
  })
}
