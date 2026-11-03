import { useSyncExternalStore } from 'react'

import { api, configurarSesion } from '@/api/cliente'
import { ErrorApi } from '@/api/errores'

import type { UsuarioSesion } from './permisos'

/**
 * Sesión del usuario (tarea 3.2, contexto 8.3):
 * - El access token vive solo en memoria (esta variable). Nunca en localStorage.
 * - El refresh token viaja en la cookie HttpOnly "helpdesk_refresh"; el JavaScript no la ve.
 * - Al abrir o recargar la página se llama a /api/auth/refresh para recuperar la sesión.
 * - Un minuto antes de que venza el access token se renueva solo.
 * - La renovación es una sola a la vez: dentro de la pestaña se comparte la misma promesa, y entre
 *   pestañas se usa un candado del navegador (Web Locks), porque cada /refresh rota la cookie.
 * - Entrar y salir se avisa a las otras pestañas con un BroadcastChannel.
 */

export type EstadoSesion =
  | { estado: 'iniciando' }
  | { estado: 'anonima'; motivo?: 'expirada' | 'salio' | 'password' }
  | { estado: 'activa'; usuario: UsuarioSesion; token: string; expiraEn: number }

type SesionResponse = {
  accessToken: string
  tipo: string
  expiraEn: string
  usuario: UsuarioSesion
}

const MARGEN_RENOVACION_MS = 60_000
const CANDADO = 'helpdesk-renovar-sesion'

let estado: EstadoSesion = { estado: 'iniciando' }
const oyentes = new Set<() => void>()
let temporizador: ReturnType<typeof setTimeout> | undefined
let renovacionEnCurso: Promise<boolean> | null = null

const canal = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('helpdesk-sesion') : null

function fijar(nuevo: EstadoSesion) {
  estado = nuevo
  clearTimeout(temporizador)
  if (nuevo.estado === 'activa') {
    const espera = Math.max(nuevo.expiraEn - Date.now() - MARGEN_RENOVACION_MS, 5_000)
    temporizador = setTimeout(() => void renovar(), espera)
  }
  oyentes.forEach((o) => o())
}

function aplicar(r: SesionResponse) {
  fijar({ estado: 'activa', usuario: r.usuario, token: r.accessToken, expiraEn: Date.parse(r.expiraEn) })
}

async function pedirRenovacion(): Promise<boolean> {
  try {
    aplicar(await api<SesionResponse>('/api/auth/refresh', { metodo: 'POST', autenticada: false }))
    return true
  } catch (e) {
    if (e instanceof ErrorApi && e.estado === 0) {
      // Sin conexión: no se cierra la sesión por un corte de red; si había token, se conserva.
      return estado.estado === 'activa'
    }
    return false
  }
}

/** Renueva el access token con la cookie. Una sola renovación a la vez, en esta y en las otras pestañas. */
export function renovar(): Promise<boolean> {
  if (!renovacionEnCurso) {
    const tarea =
      typeof navigator !== 'undefined' && navigator.locks
        ? navigator.locks.request(CANDADO, () => pedirRenovacion())
        : pedirRenovacion()
    renovacionEnCurso = tarea.finally(() => {
      renovacionEnCurso = null
    })
  }
  return renovacionEnCurso
}

/** Al cargar la aplicación: intenta recuperar la sesión con la cookie. */
export async function restaurarSesion() {
  const ok = await renovar()
  if (!ok) fijar({ estado: 'anonima' })
}

export async function iniciarSesion(email: string, password: string) {
  const r = await api<SesionResponse>('/api/auth/login', {
    metodo: 'POST',
    cuerpo: { email, password },
    autenticada: false,
  })
  aplicar(r)
  canal?.postMessage('entro')
}

/** Cierra la sesión en el servidor (borra la cookie) y en todas las pestañas. */
export async function cerrarSesion(motivo: 'salio' | 'password' = 'salio') {
  try {
    await api<void>('/api/auth/logout', { metodo: 'POST', autenticada: false })
  } catch {
    // Aunque falle la red, la sesión se cierra en esta pestaña.
  }
  fijar({ estado: 'anonima', motivo })
  canal?.postMessage('salio')
}

canal?.addEventListener('message', (e: MessageEvent) => {
  if (e.data === 'salio' && estado.estado === 'activa') fijar({ estado: 'anonima', motivo: 'salio' })
  if (e.data === 'entro' && estado.estado !== 'activa') void restaurarSesion()
})

configurarSesion({
  token: () => (estado.estado === 'activa' ? estado.token : null),
  renovar,
  alExpirar: () => {
    if (estado.estado === 'activa') fijar({ estado: 'anonima', motivo: 'expirada' })
  },
})

function suscribir(oyente: () => void) {
  oyentes.add(oyente)
  return () => oyentes.delete(oyente)
}

export function useSesion(): EstadoSesion {
  return useSyncExternalStore(suscribir, () => estado)
}

/** Usuario de la sesión activa, o null. */
export function useUsuario(): UsuarioSesion | null {
  const s = useSesion()
  return s.estado === 'activa' ? s.usuario : null
}
