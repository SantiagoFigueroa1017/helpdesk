/**
 * Une clases de Tailwind y descarta las vacías: cn('px-3', activo && 'bg-primario').
 * No resuelve choques entre clases: los componentes no deben recibir una clase que pise otra suya.
 */
export function cn(...clases: Array<string | false | null | undefined>): string {
  return clases.filter(Boolean).join(' ')
}
