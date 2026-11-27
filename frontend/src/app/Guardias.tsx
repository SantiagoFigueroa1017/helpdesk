import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { Cargando } from '@/components/ui'
import { tieneAlguno, type Permiso } from '@/features/auth/permisos'
import { useSesion, useUsuario } from '@/features/auth/sesion'

import { Layout } from './layout/Layout'
import { SinAcceso } from './paginas'

/**
 * Rutas con sesión. Mientras se recupera la sesión con la cookie muestra "Cargando"; sin sesión
 * manda al login y recuerda a dónde quería ir (?volver=).
 */
export function RutaConSesion() {
  const sesion = useSesion()
  const ubicacion = useLocation()

  if (sesion.estado === 'iniciando') {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Cargando texto="Recuperando tu sesión…" />
      </div>
    )
  }
  if (sesion.estado === 'anonima') {
    // Solo se recuerda la página cuando la sesión venció o se abrió un enlace sin sesión. Si el usuario
    // cerró sesión (o cambió la contraseña), quien entre después empieza en Inicio: puede ser otra
    // persona, con otros permisos.
    const recordar = sesion.motivo !== 'salio' && sesion.motivo !== 'password'
    const volver = ubicacion.pathname + ubicacion.search
    const destino = !recordar || volver === '/' ? '/login' : `/login?volver=${encodeURIComponent(volver)}`
    return <Navigate to={destino} replace />
  }
  return <Layout usuario={sesion.usuario} />
}

/**
 * Muestra la página solo si el usuario tiene alguno de los permisos. Es una ayuda de navegación: la
 * protección real la hace el backend en cada endpoint (403 ACCESO_DENEGADO).
 */
export function RequierePermiso({ permisos, children }: { permisos: Permiso[]; children: ReactNode }) {
  const usuario = useUsuario()
  return tieneAlguno(usuario, permisos) ? children : <SinAcceso />
}
