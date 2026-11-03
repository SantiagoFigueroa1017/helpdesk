import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2, UserPlus } from 'lucide-react'

import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { BuscadorUsuario, type UsuarioElegido } from '@/components/BuscadorUsuario'
import { Aviso, Boton, Cargando, Dialogo, Insignia } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { formato } from '@/lib/dominio'

import type { Capacitacion, Inscripcion } from './tipos'

/**
 * Inscritos de una capacitación (HU38), asistencia en lote (HU39) e inscripción hecha por el
 * personal (HU37). La asistencia se marca desde la hora de inicio; se guarda solo lo que cambió.
 */
export function Inscritos({ capacitacion: c, onCerrar }: { capacitacion: Capacitacion; onCerrar: () => void }) {
  const { puede } = usePermisos()
  const cliente = useQueryClient()
  const [marcas, setMarcas] = useState<Record<string, boolean>>({})
  const [nuevo, setNuevo] = useState<UsuarioElegido | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const clave = ['inscripciones', 'de', c.id]
  const [empezo] = useState(() => new Date(c.fechaInicio).getTime() <= Date.now())

  const q = useQuery({
    queryKey: clave,
    queryFn: ({ signal }) => api<Pagina<Inscripcion>>(`/api/capacitaciones/${c.id}/inscripciones`, { params: { tamano: 100 }, signal }),
  })
  const refrescar = () => {
    void cliente.invalidateQueries({ queryKey: clave })
    void cliente.invalidateQueries({ queryKey: ['capacitaciones'] })
  }
  const guardarAsistencia = useMutation({
    mutationFn: () =>
      api<{ marcadas: number }>(`/api/capacitaciones/${c.id}/asistencia`, {
        metodo: 'PUT',
        cuerpo: { marcas: Object.entries(marcas).map(([inscripcionId, asistio]) => ({ inscripcionId, asistio })) },
      }),
    onSuccess: (r) => { setMarcas({}); setAviso(`Asistencia guardada (${r.marcadas}).`); refrescar() },
  })
  const inscribir = useMutation({
    mutationFn: (usuarioId: string) => api<Inscripcion>(`/api/capacitaciones/${c.id}/inscripciones`, { metodo: 'POST', cuerpo: { usuarioId } }),
    onSuccess: () => { setNuevo(null); setAviso('Usuario inscrito.'); refrescar() },
  })
  const quitar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/inscripciones/${id}`, { metodo: 'DELETE' }),
    onSuccess: refrescar,
  })

  const marca = puede('ASISTENCIA_MARCAR') && empezo && c.estado !== 'CANCELADA'
  const cambios = Object.keys(marcas).length
  const error = guardarAsistencia.error ?? inscribir.error ?? quitar.error

  return (
    <Dialogo
      abierto
      onCerrar={onCerrar}
      titulo={`Inscritos · ${c.titulo}`}
      descripcion={`${c.cuposOcupados} de ${c.cupo} cupos · ${formato.fechaHora(c.fechaInicio)}`}
      acciones={
        <>
          <Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>
          {marca && <Boton disabled={cambios === 0} cargando={guardarAsistencia.isPending} onClick={() => guardarAsistencia.mutate(undefined)}>Guardar asistencia{cambios > 0 && ` (${cambios})`}</Boton>}
        </>
      }
    >
      <div className="flex max-h-[60dvh] flex-col gap-4 overflow-y-auto">
        {aviso && !error && <Aviso tono="exito">{aviso}</Aviso>}
        {error && <Aviso tono="peligro">{mensajeDeError(error)}</Aviso>}
        {puede('ASISTENCIA_MARCAR') && !empezo && <p className="text-xs text-texto-suave">La asistencia se marca desde la hora de inicio.</p>}

        {puede('INSCRIPCIONES_GESTIONAR') && c.estadoVisible === 'PROGRAMADA' && (
          <form className="flex flex-col gap-2" onSubmit={(e) => { e.preventDefault(); if (nuevo) inscribir.mutate(nuevo.id) }}>
            <BuscadorUsuario etiqueta="Inscribir a un usuario" tipo="empresa" empresaId={c.empresaId ?? undefined} valor={nuevo} onCambiar={setNuevo}
              ayuda={c.tipo === 'PERSONALIZADA' ? `Usuarios de ${c.empresaNombre ?? 'la empresa'} con el rol de cliente.` : 'Usuarios de empresa con el rol de cliente.'} />
            {nuevo && <Boton type="submit" tamano="sm" icono={<UserPlus aria-hidden className="size-4" />} cargando={inscribir.isPending}>Inscribir</Boton>}
          </form>
        )}

        {q.isPending ? <Cargando /> : q.isError ? <Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso> : q.data.contenido.length === 0 ? (
          <p className="text-sm text-texto-suave">Nadie se ha inscrito todavía.</p>
        ) : (
          <ul className="divide-y divide-borde rounded-control border border-borde">
            {q.data.contenido.map((i) => {
              const valor = marcas[i.id] ?? i.asistio
              return (
                <li key={i.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5 text-sm">
                  <div className="min-w-0">
                    <p className="font-medium">{i.usuarioNombre}</p>
                    <p className="text-xs text-texto-suave">{i.empresaNombre}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {marca ? (
                      <select
                        aria-label={`Asistencia de ${i.usuarioNombre}`}
                        className="h-8 rounded-control border border-borde bg-superficie px-2 text-xs"
                        value={valor === null ? '' : String(valor)}
                        onChange={(e) => e.target.value && setMarcas((m) => ({ ...m, [i.id]: e.target.value === 'true' }))}
                      >
                        <option value="">Sin marcar</option>
                        <option value="true">Asistió</option>
                        <option value="false">No asistió</option>
                      </select>
                    ) : (
                      <Insignia tono={i.asistio === null ? 'neutro' : i.asistio ? 'exito' : 'peligro'}>
                        {i.asistio === null ? 'Sin marcar' : i.asistio ? 'Asistió' : 'No asistió'}
                      </Insignia>
                    )}
                    {puede('INSCRIPCIONES_GESTIONAR') && i.asistio === null && (
                      <Boton variante="fantasma" tamano="sm" aria-label={`Quitar a ${i.usuarioNombre}`} onClick={() => quitar.mutate(i.id)}>
                        <Trash2 aria-hidden className="size-4" />
                      </Boton>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </Dialogo>
  )
}
