import { useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'

import { ErrorApi } from '@/api/errores'
import { restaurarSesion, useSesion } from '@/features/auth/sesion'

import { router } from './rutas'

/**
 * Datos del servidor con TanStack Query: 30 s frescos, sin recargar al volver a la pestaña y con
 * reintento solo para errores del servidor o de red (un 4xx no cambia al repetirlo).
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (intentos, error) =>
        intentos < 2 && error instanceof ErrorApi && (error.estado === 0 || error.estado >= 500),
    },
  },
})

/** Raíz: recupera la sesión al abrir la página y borra los datos en caché al cerrarla. */
export function App() {
  const sesion = useSesion()

  useEffect(() => {
    void restaurarSesion()
  }, [])

  useEffect(() => {
    if (sesion.estado === 'anonima') queryClient.clear()
  }, [sesion.estado])

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
