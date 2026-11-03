import { useMemo } from 'react'

import { tieneAlguno, type Permiso } from './permisos'
import { useUsuario } from './sesion'

/** Permisos del usuario de la sesión para decidir qué mostrar: puede('TICKETS_CREAR'). */
export function usePermisos() {
  const usuario = useUsuario()
  return useMemo(
    () => ({
      usuario,
      puede: (...permisos: Permiso[]) => tieneAlguno(usuario, permisos),
      esPersonal: usuario?.alcance === 'GLOBAL',
    }),
    [usuario],
  )
}
