import { useCallback, useEffect, useState } from 'react'

/**
 * Tema claro u oscuro. Se guarda en localStorage (solo es una preferencia de este navegador) y se
 * aplica como data-tema en <html>. index.html lo aplica antes de pintar para que no parpadee.
 */
export type Tema = 'claro' | 'oscuro'

const CLAVE = 'helpdesk.tema'

function temaActual(): Tema {
  return document.documentElement.dataset.tema === 'oscuro' ? 'oscuro' : 'claro'
}

export function useTema() {
  const [tema, setTema] = useState<Tema>(temaActual)

  useEffect(() => {
    document.documentElement.dataset.tema = tema
    try {
      localStorage.setItem(CLAVE, tema)
    } catch {
      // Navegación privada o almacenamiento bloqueado: el tema dura solo esta pestaña.
    }
  }, [tema])

  const alternar = useCallback(() => setTema((t) => (t === 'claro' ? 'oscuro' : 'claro')), [])
  return { tema, alternar }
}
