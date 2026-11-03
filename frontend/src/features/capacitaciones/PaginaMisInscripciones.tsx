import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, Boton, Cargando, Confirmar, Encabezado, Insignia, Paginador, Tabla, Tarjeta, Td, Th, Tr, Vacio } from '@/components/ui'
import { COLOR_MODULO, ESTADO_CAPACITACION, ETIQUETA_MODULO, formato } from '@/lib/dominio'

import type { Inscripcion } from './tipos'

/** Mis inscripciones (HU38 y HU40): el cliente ve sus sesiones y cancela antes de que empiecen. */
export function PaginaMisInscripciones() {
  const cliente = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [cancelar, setCancelar] = useState<Inscripcion | null>(null)
  const q = useQuery({
    queryKey: ['inscripciones', 'mias', pagina],
    queryFn: ({ signal }) => api<Pagina<Inscripcion>>('/api/inscripciones/mias', { params: { pagina, orden: 'fechaCreacion,desc' }, signal }),
    placeholderData: (anterior) => anterior,
  })
  const quitar = useMutation({
    mutationFn: (id: string) => api<void>(`/api/inscripciones/${id}`, { metodo: 'DELETE' }),
    onSuccess: () => {
      setCancelar(null)
      void cliente.invalidateQueries({ queryKey: ['inscripciones'] })
      void cliente.invalidateQueries({ queryKey: ['capacitaciones'] })
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado titulo="Mis inscripciones" descripcion="Capacitaciones en las que estás inscrito y tu asistencia." />
      <Tarjeta sinRelleno>
        {q.isPending ? <Cargando /> : q.isError ? <div className="p-4"><Aviso tono="peligro">{mensajeDeError(q.error)}</Aviso></div> : q.data.contenido.length === 0 ? (
          <Vacio titulo="No tienes inscripciones">Inscríbete desde Capacitaciones.</Vacio>
        ) : (
          <>
            <Tabla>
              <thead><tr><Th>Capacitación</Th><Th>Módulo</Th><Th>Fecha</Th><Th>Estado</Th><Th>Asistencia</Th><Th /></tr></thead>
              <tbody>
                {q.data.contenido.map((i) => (
                  <Tr key={i.id}>
                    <Td className="font-medium">{i.capacitacionTitulo}</Td>
                    <Td><Insignia tono="primario" punto={COLOR_MODULO[i.capacitacionModulo]}>{ETIQUETA_MODULO[i.capacitacionModulo]}</Insignia></Td>
                    <Td className="whitespace-nowrap text-texto-suave">{formato.fechaHora(i.capacitacionFechaInicio)}</Td>
                    <Td><Insignia tono={ESTADO_CAPACITACION[i.capacitacionEstadoVisible].tono}>{ESTADO_CAPACITACION[i.capacitacionEstadoVisible].etiqueta}</Insignia></Td>
                    <Td>
                      <Insignia tono={i.asistio === null ? 'neutro' : i.asistio ? 'exito' : 'peligro'}>
                        {i.asistio === null ? 'Sin marcar' : i.asistio ? 'Asistí' : 'No asistí'}
                      </Insignia>
                    </Td>
                    <Td className="text-right">
                      {i.capacitacionEstadoVisible === 'PROGRAMADA' && (
                        <Boton tamano="sm" variante="secundario" onClick={() => { quitar.reset(); setCancelar(i) }}>Cancelar</Boton>
                      )}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Tabla>
            <Paginador pagina={q.data.pagina} totalPaginas={q.data.totalPaginas} totalElementos={q.data.totalElementos} onCambiar={setPagina} />
          </>
        )}
      </Tarjeta>
      <Confirmar abierto={cancelar !== null} onCerrar={() => setCancelar(null)} onConfirmar={() => cancelar && quitar.mutate(cancelar.id)}
        cargando={quitar.isPending} error={quitar.error} titulo="¿Cancelar tu inscripción?" textoConfirmar="Cancelar inscripción">
        Liberas tu cupo en «{cancelar?.capacitacionTitulo}». Puedes volver a inscribirte si quedan cupos.
      </Confirmar>
    </div>
  )
}
