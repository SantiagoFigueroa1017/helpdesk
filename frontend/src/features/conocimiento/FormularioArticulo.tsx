import { useMutation, useQueryClient } from '@tanstack/react-query'

import { api } from '@/api/cliente'
import { AreaTexto, CampoTexto, DialogoFormulario, Selector } from '@/components/ui'
import { opcionesModulo, type ModuloErp, type Visibilidad } from '@/lib/dominio'
import { useFormulario } from '@/lib/formulario'

import type { Articulo } from './tipos'

/** Valores iniciales de un artículo nuevo, por ejemplo al documentar la solución de un ticket. */
export type ArticuloInicial = {
  titulo?: string
  contenido?: string
  modulo?: ModuloErp
  visibilidad?: Visibilidad
  /** Tickets que quedan vinculados desde que se crea el artículo (HU29). */
  ticketIds?: string[]
}

/** Crear (HU29) o editar (HU31) un artículo. Las etiquetas se escriben separadas por comas. */
export function FormularioArticulo({
  articulo,
  inicial,
  onCerrar,
  onGuardado,
}: {
  articulo?: Articulo
  inicial?: ArticuloInicial
  onCerrar: () => void
  onGuardado: (a: Articulo) => void
}) {
  const cliente = useQueryClient()
  const form = useFormulario({
    titulo: articulo?.titulo ?? inicial?.titulo ?? '',
    contenido: articulo?.contenido ?? inicial?.contenido ?? '',
    modulo: (articulo?.modulo as string) ?? inicial?.modulo ?? '',
    etiquetas: articulo?.etiquetas.join(', ') ?? '',
    visibilidad: (articulo?.visibilidad as string) ?? inicial?.visibilidad ?? 'PUBLICO',
  })
  const v = form.valores
  const vinculados = articulo ? (articulo.ticketIds ?? []) : (inicial?.ticketIds ?? [])

  const guardar = useMutation({
    mutationFn: () =>
      api<Articulo>(articulo ? `/api/articulos/${articulo.id}` : '/api/articulos', {
        metodo: articulo ? 'PUT' : 'POST',
        cuerpo: {
          titulo: v.titulo,
          contenido: v.contenido,
          modulo: v.modulo || null,
          etiquetas: v.etiquetas.split(',').map((e) => e.trim()).filter(Boolean),
          visibilidad: v.visibilidad,
          ticketIds: vinculados,
        },
      }),
    onSuccess: (a) => {
      void cliente.invalidateQueries({ queryKey: ['articulos'] })
      onGuardado(a)
    },
    onError: form.fallo,
  })

  return (
    <DialogoFormulario
      abierto
      onCerrar={onCerrar}
      titulo={articulo ? 'Editar artículo' : 'Nuevo artículo'}
      descripcion={!articulo && vinculados.length > 0 ? 'El artículo quedará vinculado al ticket como su solución.' : undefined}
      onEnviar={() => {
        form.limpiar()
        guardar.mutate(undefined)
      }}
      cargando={guardar.isPending}
      error={form.general}
    >
      <CampoTexto {...form.campo('titulo')} etiqueta="Título" obligatorio />
      <div className="grid gap-4 sm:grid-cols-2">
        <Selector {...form.campo('modulo')} etiqueta="Módulo" obligatorio vacio="Selecciona" opciones={opcionesModulo()} />
        <Selector
          {...form.campo('visibilidad')}
          etiqueta="Visibilidad"
          opciones={[
            { valor: 'PUBLICO', etiqueta: 'Pública: la ven los clientes' },
            { valor: 'INTERNO', etiqueta: 'Interna: solo el personal' },
          ]}
        />
      </div>
      <CampoTexto {...form.campo('etiquetas')} etiqueta="Etiquetas" ayuda="Separadas por comas, por ejemplo: pasarela, pagos, PSE" />
      <AreaTexto {...form.campo('contenido')} etiqueta="Contenido" obligatorio rows={10} ayuda="Pasos de la solución. Al guardar se recalcula el vector para la búsqueda por significado." />
    </DialogoFormulario>
  )
}
