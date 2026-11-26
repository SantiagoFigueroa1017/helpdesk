import { useQueries, useQuery } from '@tanstack/react-query'
import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router'

import { api, type Pagina } from '@/api/cliente'
import { Aviso, Insignia, Tarjeta } from '@/components/ui'
import { tieneAlguno, type Permiso, type UsuarioSesion } from '@/features/auth/permisos'
import { usePermisos } from '@/features/auth/usePermisos'
import { MENU } from '@/app/navegacion'
import { ESTADO_MEMBRESIA, ETIQUETA_MODULO, formato, type EstadoMembresia, type ModuloErp } from '@/lib/dominio'

/** MembresiaResponse del backend (2.5), con lo que usa el inicio. */
type Membresia = {
  id: string
  modulosContratados: ModuloErp[]
  fechaVencimiento: string
  estadoVisible: EstadoMembresia
  soporte: { habilitado: boolean; motivo: string | null }
}

const MOTIVO_SIN_SOPORTE: Record<string, string> = {
  SIN_MEMBRESIA: 'Tu empresa todavía no tiene una membresía asignada.',
  PENDIENTE: 'La membresía de tu empresa está pendiente de activación.',
  SUSPENDIDA: 'La membresía de tu empresa está suspendida por un pago fallido.',
  VENCIDA: 'La membresía de tu empresa venció.',
}

const DESCRIPCION: Record<string, string> = {
  '/tickets': 'Reporta incidentes y sugerencias, y sigue su estado.',
  '/conocimiento': 'Busca soluciones documentadas por módulo del ERP.',
  '/capacitaciones': 'Cronograma de capacitaciones masivas y personalizadas.',
  '/solicitudes': 'Solicitudes de capacitación personalizada.',
  '/mis-inscripciones': 'Las capacitaciones a las que te inscribiste.',
  '/empresas': 'Datos y contacto de las empresas clientes.',
  '/usuarios': 'Usuarios y sus roles.',
  '/planes': 'Planes, precios y límites.',
  '/membresias': 'Vigencia, módulos contratados y estado del soporte.',
  '/pagos': 'Historial de pagos de las membresías.',
  '/roles': 'Roles dinámicos y su catálogo de permisos.',
  '/reportes': 'Analítica con los reportes A1 a A9.',
}

/** Inicio: saludo, estado del soporte (usuarios de empresa) y accesos a lo que sus permisos permiten. */
export function PaginaInicio() {
  const { usuario, puede, esPersonal } = usePermisos()
  if (!usuario) return null

  const accesos = MENU.flatMap((s) => s.entradas).filter((e) => tieneAlguno(usuario, e.permisos))
  const nombre = usuario.nombre.split(/\s+/)[0]

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Hola, {nombre}</h1>
        <p className="mt-1 text-texto-suave">
          {esPersonal ? 'Personal de Sistemas Integrados.' : 'Usuario de una empresa cliente.'} Tienes{' '}
          {usuario.permisos.length} {usuario.permisos.length === 1 ? 'permiso' : 'permisos'} según tus roles.
        </p>
      </div>

      {!esPersonal && puede('MEMBRESIAS_VER') && <EstadoSoporte />}

      <Pendientes usuario={usuario} />

      <section aria-labelledby="accesos">
        <h2 id="accesos" className="mb-3 text-sm font-semibold text-texto-suave">
          Accesos
        </h2>
        {accesos.length === 0 ? (
          <Aviso tono="info">Tus roles todavía no incluyen permisos sobre ningún módulo.</Aviso>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {accesos.map((e) => {
              const Icono = e.icono
              return (
                <li key={e.ruta}>
                  <Link
                    to={e.ruta}
                    className="group flex h-full items-start gap-3 rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta transition-colors hover:border-primario/40"
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-control bg-primario-suave text-sobre-primario-suave">
                      <Icono aria-hidden className="size-5" />
                    </span>
                    <span className="flex-1">
                      <span className="flex items-center gap-1 font-semibold text-texto">
                        {(!esPersonal && e.etiquetaEmpresa) || e.etiqueta}
                        <ArrowRight aria-hidden className="size-4 opacity-0 transition-opacity group-hover:opacity-100" />
                      </span>
                      <span className="mt-0.5 block text-sm text-texto-suave">{DESCRIPCION[e.ruta]}</span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}

/** Membresía de la empresa del usuario: un usuario de empresa solo recibe la suya (filtro EMPRESA). */
function EstadoSoporte() {
  const consulta = useQuery({
    queryKey: ['membresias', 'propia'],
    queryFn: ({ signal }) => api<Pagina<Membresia>>('/api/membresias', { params: { tamano: 1 }, signal }),
  })

  if (consulta.isPending) return null
  if (consulta.isError) return <Aviso tono="aviso">No se pudo consultar el estado de tu membresía.</Aviso>

  const m = consulta.data.contenido[0]
  if (!m) {
    return (
      <Aviso tono="peligro" titulo="Sin soporte">
        {MOTIVO_SIN_SOPORTE.SIN_MEMBRESIA} Mientras tanto no puedes crear tickets ni inscribirte a capacitaciones.
      </Aviso>
    )
  }
  if (!m.soporte.habilitado) {
    return (
      <Aviso tono="peligro" titulo="Soporte bloqueado">
        {MOTIVO_SIN_SOPORTE[m.soporte.motivo ?? ''] ?? 'Tu empresa no tiene soporte en este momento.'} Puedes consultar
        tus tickets, tu membresía y tus pagos, pero no crear tickets ni inscribirte hasta ponerte al día.
      </Aviso>
    )
  }
  const estado = ESTADO_MEMBRESIA[m.estadoVisible]
  return (
    <Tarjeta titulo="Tu soporte" acciones={<Insignia tono={estado.tono}>{estado.etiqueta}</Insignia>}>
      <p className="text-sm text-texto-suave">
        Vigente hasta el <span className="font-medium text-texto">{formato.fecha(m.fechaVencimiento)}</span>. Módulos
        contratados:
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        {m.modulosContratados.map((mod) => (
          <Insignia key={mod} tono="primario">
            {ETIQUETA_MODULO[mod]}
          </Insignia>
        ))}
      </div>
    </Tarjeta>
  )
}

type Contador = { etiqueta: string; ruta: string; api: string; params: Record<string, string | boolean> }

/** Qué tiene pendiente cada usuario según sus permisos, con enlace a la bandeja ya filtrada. */
function contadoresDe(usuario: UsuarioSesion): Contador[] {
  const puede = (p: Permiso) => usuario.permisos.includes(p)
  const lista: Contador[] = []
  if (usuario.alcance === 'GLOBAL') {
    if (puede('TICKETS_ASIGNAR')) {
      lista.push(
        { etiqueta: 'Asignados a mí sin empezar', ruta: '/tickets?mios=1&estado=ABIERTO', api: '/api/tickets', params: { agenteId: usuario.id, estado: 'ABIERTO' } },
        { etiqueta: 'Asignados a mí en proceso', ruta: '/tickets?mios=1&estado=EN_PROCESO', api: '/api/tickets', params: { agenteId: usuario.id, estado: 'EN_PROCESO' } },
      )
    }
    if (puede('TICKETS_VER')) {
      lista.push({ etiqueta: 'Tickets sin asignar', ruta: '/tickets?sinAsignar=1&estado=ABIERTO', api: '/api/tickets', params: { sinAsignar: true, estado: 'ABIERTO' } })
    }
    if (puede('SOLICITUDES_GESTIONAR')) {
      lista.push({ etiqueta: 'Solicitudes de capacitación pendientes', ruta: '/solicitudes', api: '/api/solicitudes-capacitacion', params: { estado: 'PENDIENTE' } })
    }
  } else if (puede('TICKETS_VER') || puede('TICKETS_VER_PROPIOS')) {
    const de = puede('TICKETS_VER') ? 'de tu empresa' : ''
    lista.push(
      { etiqueta: `Tickets abiertos ${de}`.trim(), ruta: '/tickets?estado=ABIERTO', api: '/api/tickets', params: { estado: 'ABIERTO' } },
      { etiqueta: `Tickets en proceso ${de}`.trim(), ruta: '/tickets?estado=EN_PROCESO', api: '/api/tickets', params: { estado: 'EN_PROCESO' } },
      { etiqueta: `Tickets resueltos ${de}`.trim(), ruta: '/tickets?estado=RESUELTO', api: '/api/tickets', params: { estado: 'RESUELTO' } },
    )
  }
  return lista
}

function Pendientes({ usuario }: { usuario: UsuarioSesion }) {
  const contadores = contadoresDe(usuario)
  const totales = useQueries({
    queries: contadores.map((c) => ({
      queryKey: ['inicio', 'contador', c.api, c.params],
      queryFn: ({ signal }: { signal: AbortSignal }) => api<Pagina<unknown>>(c.api, { params: { ...c.params, tamano: 1 }, signal }),
      select: (p: Pagina<unknown>) => p.totalElementos,
    })),
  })
  if (contadores.length === 0) return null

  return (
    <section aria-labelledby="pendientes">
      <h2 id="pendientes" className="mb-3 text-sm font-semibold text-texto-suave">
        {usuario.alcance === 'GLOBAL' ? 'Tu trabajo' : 'Tus tickets'}
      </h2>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {contadores.map((c, i) => {
          const t = totales[i]
          return (
            <li key={c.ruta}>
              <Link
                to={c.ruta}
                className="flex h-full flex-col rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta transition-colors hover:border-primario/40"
              >
                <span className="text-sm text-texto-suave">{c.etiqueta}</span>
                <span className="mt-1 text-2xl font-semibold tracking-tight text-texto tabular-nums">
                  {t.isPending ? '…' : t.isError ? '—' : t.data}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
