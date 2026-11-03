import { useEffect, useState } from 'react'

/** Devuelve el valor cuando deja de cambiar durante "ms" milisegundos (búsquedas mientras se escribe). */
export function useRetardo<T>(valor: T, ms: number): T {
  const [retrasado, setRetrasado] = useState(valor)
  useEffect(() => {
    const t = setTimeout(() => setRetrasado(valor), ms)
    return () => clearTimeout(t)
  }, [valor, ms])
  return retrasado
}
