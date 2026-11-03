import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate } from 'react-router'

import { useMembresia } from '@/api/catalogos'
import { api } from '@/api/cliente'
import { ErrorApi } from '@/api/errores'
import { BuscadorUsuario, type UsuarioElegido } from '@/components/BuscadorUsuario'
import { AreaTexto, Aviso, Boton, CampoTexto, Cargando, Selector, Tarjeta } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { MOTIVO_SIN_SOPORTE, opcionesModulo, type ModuloErp, type TipoTicket } from '@/lib/dominio'
import { useFormulario } from '@/lib/formulario'

import { CamposDetalle } from './CamposDetalle'
import { detalleParaEnviar, useCamposModulo, type Ticket } from './tipos'

/**
 * Crear un ticket (HU21). El cliente solo ve los módulos que su empresa contrató; el personal de
 * soporte primero elige al solicitante y se le muestran los módulos de la empresa de esa persona.
 * Para un incidente aparecen los campos del módulo; una sugerencia no los lleva.
 */
export function PaginaTicketNuevo() {
  const { esPersonal } = usePermisos()
  const navegar = useNavigate()
  const cliente = useQueryClient()
  const campos = useCamposModulo()
  const [solicitante, setSolicitante] = useState<UsuarioElegido | null>(null)
  const [detalle, setDetalle] = useState<Record<string, string>>({})
  const form = useFormulario({ modulo: '', tipo: 'INCIDENTE', titulo: '', descripcion: '', detalle: '', solicitanteId: '' })
  const { valores } = form

  const membresia = useMembresia(esPersonal ? solicitante?.empresaId : null, !esPersonal || Boolean(solicitante))
  const contratados = membresia.data?.modulosContratados ?? []
  const camposModulo = campos.data?.find((c) => c.modulo === valores.modulo)?.campos ?? []

  const crear = useMutation({
    mutationFn: () =>
      api<Ticket>('/api/tickets', {
        metodo: 'POST',
        cuerpo: {
          solicitanteId: esPersonal ? (solicitante?.id ?? null) : null,
          modulo: valores.modulo || null,
          tipo: valores.tipo,
          titulo: valores.titulo,
          descripcion: valores.descripcion,
          detalle: valores.tipo === 'INCIDENTE' ? detalleParaEnviar(camposModulo, detalle) : null,
        },
      }),
    onSuccess: (t) => {
      void cliente.invalidateQueries({ queryKey: ['tickets'] })
      navegar(`/tickets/${t.id}`, { replace: true })
    },
    onError: form.fallo,
  })

  const bloqueo = crear.error instanceof ErrorApi && crear.error.codigo === 'SOPORTE_BLOQUEADO' ? crear.error : null
  const sinSoporte = membresia.data && !membresia.data.soporte.habilitado ? membresia.data.soporte.motivo : null

  if (campos.isPending) return <Cargando />

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <Link to="/tickets" className="inline-flex items-center gap-1 text-sm text-texto-suave hover:text-texto">
          <ArrowLeft aria-hidden className="size-4" /> Tickets
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">Nuevo ticket</h1>
        <p className="mt-1 text-texto-suave">Reporta un incidente del ERP o deja una sugerencia de mejora.</p>
      </div>

      {(bloqueo || sinSoporte) && (
        <Aviso tono="peligro" titulo="Soporte bloqueado">
          {esPersonal ? 'La empresa de este solicitante no tiene soporte: ' : 'No puedes crear tickets: '}
          {MOTIVO_SIN_SOPORTE[(bloqueo?.motivo ?? sinSoporte) || ''] ?? 'la membresía no está vigente'}.
        </Aviso>
      )}

      <Tarjeta>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            form.limpiar()
            crear.mutate(undefined)
          }}
        >
          {form.general && !bloqueo && <Aviso tono="peligro">{form.general}</Aviso>}

          {esPersonal && (
            <BuscadorUsuario
              etiqueta="Solicitante"
              obligatorio
              tipo="empresa"
              valor={solicitante}
              onCambiar={(u) => {
                setSolicitante(u)
                form.poner('modulo', '')
              }}
              error={form.errores.solicitanteId}
              ayuda="El ticket queda a nombre de esta persona y de su empresa."
            />
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <Selector
              {...form.campo('modulo')}
              etiqueta="Módulo del ERP"
              obligatorio
              vacio={esPersonal && !solicitante ? 'Primero elige el solicitante' : 'Selecciona un módulo'}
              disabled={esPersonal && !solicitante}
              opciones={opcionesModulo(contratados as ModuloErp[])}
              ayuda={membresia.data === null ? 'La empresa no tiene membresía.' : 'Solo los módulos contratados en la membresía.'}
              onChange={(e) => {
                form.poner('modulo', e.target.value)
                setDetalle({})
              }}
            />
            <Selector
              {...form.campo('tipo')}
              etiqueta="Tipo"
              obligatorio
              opciones={[
                { valor: 'INCIDENTE', etiqueta: 'Incidente: algo no funciona' },
                { valor: 'SUGERENCIA', etiqueta: 'Sugerencia: una mejora' },
              ]}
              onChange={(e) => form.poner('tipo', e.target.value as TipoTicket)}
            />
          </div>
          <CampoTexto {...form.campo('titulo')} etiqueta="Título" obligatorio maxLength={150} placeholder="Describe el problema en una frase" />
          <AreaTexto {...form.campo('descripcion')} etiqueta="Descripción" obligatorio rows={5} ayuda="Incluye qué hacías, qué esperabas y qué pasó." />

          {valores.tipo === 'INCIDENTE' && camposModulo.length > 0 && (
            <CamposDetalle
              campos={camposModulo}
              valores={detalle}
              errores={form.errores}
              onCambiar={(n, v) => setDetalle((d) => ({ ...d, [n]: v }))}
            />
          )}

          <div className="flex justify-end gap-2">
            <Boton variante="secundario" onClick={() => navegar('/tickets')}>Cancelar</Boton>
            <Boton type="submit" cargando={crear.isPending} disabled={Boolean(sinSoporte)}>Crear ticket</Boton>
          </div>
        </form>
      </Tarjeta>
    </div>
  )
}
