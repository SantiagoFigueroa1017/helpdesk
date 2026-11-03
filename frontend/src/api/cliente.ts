import { ErrorApi } from './errores'

/**
 * Cliente HTTP de la API (tarea 3.2). Todas las llamadas al backend pasan por aquí.
 *
 * - Agrega "Authorization: Bearer <access token>" con el token que tiene la sesión en memoria.
 * - Ante un 401 NO_AUTENTICADO o TOKEN_INVALIDO renueva la sesión una sola vez (la renovación es
 *   compartida: si diez peticiones fallan a la vez, sale un solo /refresh) y repite la petición.
 * - Convierte los errores ProblemDetail del backend en ErrorApi.
 *
 * La sesión (features/auth/sesion.ts) se conecta con configurarSesion; así este archivo no depende
 * de React ni de la sesión.
 */

type Conexion = {
  token: () => string | null
  /** Renueva el access token con la cookie. true si quedó una sesión válida. */
  renovar: () => Promise<boolean>
  /** La sesión no se pudo renovar: hay que volver al login. */
  alExpirar: () => void
}

let conexion: Conexion = {
  token: () => null,
  renovar: async () => false,
  alExpirar: () => {},
}

export function configurarSesion(c: Conexion) {
  conexion = c
}

export type Parametros = Record<string, string | number | boolean | null | undefined>

export type OpcionesApi = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  cuerpo?: unknown
  params?: Parametros
  signal?: AbortSignal
  /** false para las rutas públicas de /api/auth: no envía token ni intenta renovar. */
  autenticada?: boolean
}

const RENOVABLES = new Set(['NO_AUTENTICADO', 'TOKEN_INVALIDO'])

export function construirRuta(ruta: string, params?: Parametros): string {
  if (!params) return ruta
  const busqueda = new URLSearchParams()
  for (const [clave, valor] of Object.entries(params)) {
    if (valor !== undefined && valor !== null && valor !== '') busqueda.append(clave, String(valor))
  }
  const texto = busqueda.toString()
  return texto ? `${ruta}?${texto}` : ruta
}

async function enviar(ruta: string, o: OpcionesApi): Promise<Response> {
  const cabeceras: Record<string, string> = { Accept: 'application/json' }
  if (o.cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'
  const token = o.autenticada === false ? null : conexion.token()
  if (token) cabeceras.Authorization = `Bearer ${token}`
  try {
    return await fetch(construirRuta(ruta, o.params), {
      method: o.metodo ?? 'GET',
      headers: cabeceras,
      body: o.cuerpo === undefined ? undefined : JSON.stringify(o.cuerpo),
      credentials: 'same-origin',
      signal: o.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw ErrorApi.sinConexion()
  }
}

/** Llama a la API y devuelve el JSON de la respuesta (undefined en un 204). Lanza ErrorApi. */
export async function api<T>(ruta: string, opciones: OpcionesApi = {}): Promise<T> {
  let respuesta = await enviar(ruta, opciones)

  if (respuesta.status === 401 && opciones.autenticada !== false) {
    const error = await ErrorApi.desdeRespuesta(respuesta.clone())
    if (!RENOVABLES.has(error.codigo)) throw error
    if (await conexion.renovar()) {
      respuesta = await enviar(ruta, opciones)
    } else {
      conexion.alExpirar()
      throw error
    }
  }

  if (!respuesta.ok) throw await ErrorApi.desdeRespuesta(respuesta)
  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}

/** Respuesta de todo listado paginado del backend (common/paginacion/Pagina). */
export type Pagina<T> = {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}
