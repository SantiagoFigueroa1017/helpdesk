/**
 * Error de la API en el formato ProblemDetail del backend (contexto 8.2). El frontend decide con
 * "codigo" (nombre de CodigoError), nunca con el texto, que puede cambiar.
 */
export type ErrorCampo = { campo: string; mensaje: string }

type Problema = {
  title?: string
  detail?: string
  codigo?: string
  errores?: ErrorCampo[]
  motivo?: string
  dependientes?: Record<string, number>
  idError?: string
}

export class ErrorApi extends Error {
  readonly estado: number
  readonly codigo: string
  readonly titulo: string
  readonly errores: ErrorCampo[]
  /** SOPORTE_BLOQUEADO: SIN_MEMBRESIA, PENDIENTE, SUSPENDIDA o VENCIDA. */
  readonly motivo?: string
  /** TIENE_DEPENDIENTES: conteo por colección. */
  readonly dependientes?: Record<string, number>
  /** DEMASIADOS_INTENTOS: segundos de la cabecera Retry-After. */
  readonly reintentarEn?: number
  readonly idError?: string

  constructor(estado: number, problema: Problema, reintentarEn?: number) {
    super(problema.detail || problema.title || `Error ${estado}`)
    this.name = 'ErrorApi'
    this.estado = estado
    this.codigo = problema.codigo ?? (estado === 0 ? 'SIN_CONEXION' : 'ERROR_DESCONOCIDO')
    this.titulo = problema.title ?? 'Error'
    this.errores = problema.errores ?? []
    this.motivo = problema.motivo
    this.dependientes = problema.dependientes
    this.reintentarEn = reintentarEn
    this.idError = problema.idError
  }

  static async desdeRespuesta(r: Response): Promise<ErrorApi> {
    let problema: Problema = {}
    try {
      problema = (await r.json()) as Problema
    } catch {
      // Respuesta sin cuerpo JSON (por ejemplo, el proxy de Vite cuando el backend está apagado).
    }
    const retry = Number(r.headers.get('Retry-After'))
    return new ErrorApi(r.status, problema, Number.isFinite(retry) && retry > 0 ? retry : undefined)
  }

  static sinConexion(): ErrorApi {
    return new ErrorApi(0, {
      codigo: 'SIN_CONEXION',
      title: 'Sin conexión',
      detail: 'No se pudo conectar con el servidor. Revisa que el backend esté encendido.',
    })
  }

  /** Mensaje de error de un campo, por ejemplo errorDe('email') o errorDe('detalle.urlError'). */
  errorDe(campo: string): string | undefined {
    return this.errores.find((e) => e.campo === campo)?.mensaje
  }
}

/** Mensaje para mostrar al usuario, sea o no un ErrorApi. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorApi) return error.message
  return 'Ocurrió un error inesperado. Intenta de nuevo.'
}

const COLECCION: Record<string, string> = {
  usuarios: 'usuarios',
  membresias: 'membresías',
  pagos: 'pagos',
  tickets: 'tickets',
  capacitaciones: 'capacitaciones',
  inscripciones: 'inscripciones',
  solicitudesCapacitacion: 'solicitudes de capacitación',
  comentariosTicket: 'comentarios',
  articulos: 'artículos',
}

/** Mensaje de un error al eliminar. Con TIENE_DEPENDIENTES lista qué registros lo impiden. */
export function mensajeEliminar(e: unknown): string {
  if (e instanceof ErrorApi && e.codigo === 'TIENE_DEPENDIENTES' && e.dependientes) {
    const lista = Object.entries(e.dependientes)
      .filter(([, n]) => n > 0)
      .map(([c, n]) => `${n} ${COLECCION[c] ?? c}`)
      .join(', ')
    return lista ? `${e.message} (${lista}).` : e.message
  }
  return mensajeDeError(e)
}

