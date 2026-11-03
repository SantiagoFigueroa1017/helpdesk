import { useMutation, useQueryClient } from '@tanstack/react-query'

import { api } from '@/api/cliente'
import { AreaTexto, CampoTexto, DialogoFormulario, Selector } from '@/components/ui'
import { opcionesModulo } from '@/lib/dominio'
import { useFormulario } from '@/lib/formulario'

import type { Articulo } from './tipos'

/** Crear o editar un artículo (HU16). Las etiquetas se escriben separadas por comas. */
export function FormularioArticulo({ articulo, onCerrar, onGuardado }: { articulo?: Articulo; onCerrar: () => void; onGuardado: (a: Articulo) => void }) {
  const cliente = useQueryClient()
  const form = useFormulario({
    titulo: articulo?.titulo ?? '',
    contenido: articulo?.contenido ?? '',
    modulo: (articulo?.modulo as string) ?? '',
    etiquetas: articulo?.etiquetas.join(', ') ?? '',
    visibilidad: (articulo?.visibilidad as string) ?? 'PUBLICO',
  })
  const v = form.valores

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
          ticketIds: articulo?.ticketIds ?? [],
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
