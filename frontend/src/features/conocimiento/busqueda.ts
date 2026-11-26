import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/cliente'
import { useRetardo } from '@/lib/useRetardo'

import type { ResultadoBusqueda } from './tipos'

export type ModoBusqueda = 'texto' | 'significado'

/**
 * Búsqueda de artículos mientras se escribe (contexto 9.2): por palabras tras 300 ms y con al menos
 * 2 letras; por significado tras 800 ms y con al menos 3 palabras. La usan la base de conocimientos
 * y el panel de solución del ticket.
 */
export function useBusquedaArticulos(modo: ModoBusqueda, texto: string, modulo: string, limite?: number) {
  const buscado = useRetardo(texto.trim(), modo === 'texto' ? 300 : 800)
  const palabras = buscado.split(/\s+/).filter(Boolean).length
  const listo = modo === 'texto' ? buscado.length >= 2 : palabras >= 3

  const consulta = useQuery({
    queryKey: ['articulos', 'busqueda', modo, buscado, modulo, limite],
    queryFn: ({ signal }) =>
      modo === 'texto'
        ? api<ResultadoBusqueda>('/api/articulos/busqueda', { params: { texto: buscado, modulo, limite: limite ?? 20 }, signal })
        : api<ResultadoBusqueda>('/api/articulos/busqueda-semantica', { params: { consulta: buscado, modulo, limite: limite ?? 10 }, signal }),
    enabled: buscado.length > 0 && listo,
  })

  return { buscado, palabras, listo, consulta }
}

/** Texto del "aviso" de una búsqueda por significado que respondió con la de palabras (acta 2.7). */
const MENSAJE_AVISO: Record<string, string> = {
  GEMINI_NO_CONFIGURADO: 'La búsqueda por significado no está configurada en el servidor.',
  GEMINI_LIMITE_ALCANZADO: 'Se alcanzó el límite gratuito del servicio de búsqueda por significado.',
  GEMINI_NO_DISPONIBLE: 'El servicio de búsqueda por significado no respondió.',
  VECTORIAL_NO_DISPONIBLE: 'La búsqueda por significado solo funciona con la base de datos en Atlas.',
}

export function mensajeAviso(aviso: string): string {
  return `${MENSAJE_AVISO[aviso] ?? 'La búsqueda por significado no se pudo hacer.'} Estos resultados son de la búsqueda por palabras.`
}
