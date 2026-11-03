import type { EstadoCapacitacion, EstadoSolicitud, ModuloErp } from '@/lib/dominio'

/** Respuestas de /api/capacitaciones, /api/solicitudes-capacitacion e inscripciones (tarea 2.8). */
export type Capacitacion = {
  id: string
  titulo: string
  tipo: 'MASIVA' | 'PERSONALIZADA'
  modulo: ModuloErp
  empresaId: string | null
  empresaNombre: string | null
  fechaInicio: string
  duracionMinutos: number
  fechaFin: string
  cupo: number
  cuposOcupados: number
  cuposDisponibles: number
  instructorId: string
  instructorNombre: string
  estado: 'PROGRAMADA' | 'CANCELADA'
  estadoVisible: EstadoCapacitacion
  solicitudId: string | null
  inscritos: number | null
  miInscripcionId: string | null
}

export type Inscripcion = {
  id: string
  capacitacionId: string
  capacitacionTitulo: string
  capacitacionModulo: ModuloErp
  capacitacionFechaInicio: string
  capacitacionFechaFin: string
  capacitacionEstadoVisible: EstadoCapacitacion
  usuarioId: string
  usuarioNombre: string
  empresaId: string
  empresaNombre: string
  asistio: boolean | null
  marcadoPorNombre: string | null
  fechaMarcado: string | null
  fechaCreacion: string
}

export type Solicitud = {
  id: string
  empresaId: string
  empresaNombre: string
  solicitanteId: string
  solicitanteNombre: string
  modulo: ModuloErp
  descripcion: string
  participantesEstimados: number
  fechaPreferida: string | null
  estado: EstadoSolicitud
  capacitacionId: string | null
  motivoRechazo: string | null
  atendidaPorNombre: string | null
  fechaCreacion: string
}
