import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus, Search, Sparkles } from 'lucide-react'
import { Link, useNavigate } from 'react-router'

import { api, type Pagina } from '@/api/cliente'
import { mensajeDeError } from '@/api/errores'
import { Aviso, Boton, CampoTexto, Cargando, Encabezado, Insignia, Paginador, Selector, Tarjeta, Vacio } from '@/components/ui'
import { usePermisos } from '@/features/auth/usePermisos'
import { cn } from '@/lib/cn'
import { COLOR_MODULO, ETIQUETA_MODULO, formato, opcionesModulo, type ModuloErp, type Visibilidad } from '@/lib/dominio'
import { useRetardo } from '@/lib/useRetardo'

import { FormularioArticulo } from './FormularioArticulo'
import type { ArticuloResumen, ResultadoBusqueda } from './tipos'

type Modo = 'texto' | 'significado'

/**
 * Base de conocimientos (HU16 y HU17). Sin texto muestra el listado; al escribir busca mientras se
 * teclea: por palabras con el índice de texto (300 ms de espera) o por significado con Atlas Vector
 * Search (800 ms y al menos 3 palabras). Si la búsqueda por significado no está disponible, el
 * backend responde por palabras y explica por qué en "aviso".
 */
export function PaginaConocimiento() {
  const { puede } = usePermisos()
  const navegar = useNavigate()
  const [modo, setModo] = useState<Modo>('texto')
  const [texto, setTexto] = useState('')
  const [modulo, setModulo] = useState('')
  const [visibilidad, setVisibilidad] = useState('')
  const [pagina, setPagina] = useState(0)
  const [nuevo, setNuevo] = useState(false)

  const buscado = useRetardo(texto.trim(), modo === 'texto' ? 300 : 800)
  const palabras = buscado.split(/\s+/).filter(Boolean).length
  const buscando = buscado.length > 0
  const listo = modo === 'texto' ? buscado.length >= 2 : palabras >= 3

  const lista = useQuery({
    queryKey: ['articulos', 'lista', { modulo, visibilidad, pagina }],
    queryFn: ({ signal }) =>
      api<Pagina<ArticuloResumen>>('/api/articulos', { params: { modulo, visibilidad, pagina, orden: 'fechaCreacion,desc' }, signal }),
    enabled: !buscando,
    placeholderData: (anterior) => anterior,
  })
  const busqueda = useQuery({
    queryKey: ['articulos', 'busqueda', modo, buscado, modulo],
    queryFn: ({ signal }) =>
      modo === 'texto'
        ? api<ResultadoBusqueda>('/api/articulos/busqueda', { params: { texto: buscado, modulo, limite: 20 }, signal })
        : api<ResultadoBusqueda>('/api/articulos/busqueda-semantica', { params: { consulta: buscado, modulo, limite: 10 }, signal }),
    enabled: buscando && listo,
  })

  return (
    <div className="flex flex-col gap-6">
      <Encabezado
        titulo="Base de conocimientos"
        descripcion="Soluciones a los problemas más comunes de cada módulo del ERP."
        acciones={puede('ARTICULOS_CREAR') && <Boton icono={<Plus aria-hidden className="size-4" />} onClick={() => setNuevo(true)}>Nuevo artículo</Boton>}
      />

      <Tarjeta>
        <div className="flex flex-col gap-4">
          <div role="radiogroup" aria-label="Tipo de búsqueda" className="inline-flex w-fit rounded-control border border-borde p-0.5">
            {(
              [
                ['texto', 'Por palabras', Search],
                ['significado', 'Por significado', Sparkles],
              ] as const
            ).map(([valor, etiqueta, Icono]) => (
              <button
                key={valor}
                type="button"
                role="radio"
                aria-checked={modo === valor}
                onClick={() => setModo(valor)}
                className={cn(
                  'inline-flex cursor-pointer items-center gap-1.5 rounded-[0.4rem] px-3 py-1.5 text-sm font-medium',
                  modo === valor ? 'bg-primario text-sobre-primario' : 'text-texto-suave hover:text-texto',
                )}
              >
                <Icono aria-hidden className="size-4" />
                {etiqueta}
              </button>
            ))}
          </div>
          <div className="grid gap-3 sm:grid-cols-[1fr_14rem_12rem]">
            <CampoTexto
              etiqueta={modo === 'texto' ? 'Buscar por palabras' : 'Describe tu problema'}
              type="search"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder={modo === 'texto' ? 'pasarela pse rechazo' : 'los clientes pagan pero el pedido no se confirma'}
              ayuda={modo === 'significado' ? 'Escribe al menos 3 palabras. Encuentra artículos aunque usen otras palabras.' : undefined}
            />
            <Selector etiqueta="Módulo" vacio="Todos" value={modulo} onChange={(e) => { setModulo(e.target.value); setPagina(0) }} opciones={opcionesModulo()} />
            {puede('ARTICULOS_VER_INTERNOS') && !buscando && (
              <Selector
                etiqueta="Visibilidad"
                vacio="Todas"
                value={visibilidad}
                onChange={(e) => { setVisibilidad(e.target.value); setPagina(0) }}
                opciones={[{ valor: 'PUBLICO', etiqueta: 'Públicos' }, { valor: 'INTERNO', etiqueta: 'Internos' }]}
              />
            )}
          </div>
        </div>
      </Tarjeta>

      {buscando ? (
        !listo ? (
          <p className="text-sm text-texto-suave">{modo === 'texto' ? 'Escribe al menos 2 letras.' : `Escribe al menos 3 palabras (llevas ${palabras}).`}</p>
        ) : busqueda.isPending ? (
          <Cargando texto="Buscando…" />
        ) : busqueda.isError ? (
          <Aviso tono="peligro">{mensajeDeError(busqueda.error)}</Aviso>
        ) : (
          <div className="flex flex-col gap-3">
            {busqueda.data.aviso && <Aviso tono="aviso">{busqueda.data.aviso}</Aviso>}
            {busqueda.data.resultados.length === 0 ? (
              <Vacio titulo="No encontramos artículos">Prueba con otras palabras o crea un ticket para que te ayudemos.</Vacio>
            ) : (
              <ul className="flex flex-col gap-3">
                {busqueda.data.resultados.map((r) => (
                  <li key={r.id}>
                    <TarjetaArticulo id={r.id} titulo={r.titulo} modulo={r.modulo} visibilidad={r.visibilidad} etiquetas={r.etiquetas}>
                      <p className="mt-2 line-clamp-3 text-sm text-texto-suave">{r.extracto}</p>
                      <p className="mt-2 text-xs text-texto-suave">
                        {busqueda.data.origen === 'SEMANTICA' ? 'Similitud' : 'Relevancia'}: {r.puntaje.toFixed(2)}
                      </p>
                    </TarjetaArticulo>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      ) : lista.isPending ? (
        <Cargando texto="Cargando artículos…" />
      ) : lista.isError ? (
        <Aviso tono="peligro">{mensajeDeError(lista.error)}</Aviso>
      ) : lista.data.contenido.length === 0 ? (
        <Vacio titulo="No hay artículos" />
      ) : (
        <div className="flex flex-col gap-3">
          <ul className="grid gap-3 md:grid-cols-2">
            {lista.data.contenido.map((a) => (
              <li key={a.id}>
                <TarjetaArticulo id={a.id} titulo={a.titulo} modulo={a.modulo} visibilidad={a.visibilidad} etiquetas={a.etiquetas}>
                  <p className="mt-2 text-xs text-texto-suave">
                    {a.autorNombre} · {formato.fecha(a.fechaActualizacion)} · {a.ticketsVinculados}{' '}
                    {a.ticketsVinculados === 1 ? 'ticket resuelto' : 'tickets resueltos'}
                  </p>
                </TarjetaArticulo>
              </li>
            ))}
          </ul>
          <Tarjeta sinRelleno>
            <Paginador pagina={lista.data.pagina} totalPaginas={lista.data.totalPaginas} totalElementos={lista.data.totalElementos} onCambiar={setPagina} />
          </Tarjeta>
        </div>
      )}

      {nuevo && <FormularioArticulo onCerrar={() => setNuevo(false)} onGuardado={(a) => navegar(`/conocimiento/${a.id}`)} />}
    </div>
  )
}

function TarjetaArticulo({
  id,
  titulo,
  modulo,
  visibilidad,
  etiquetas,
  children,
}: {
  id: string
  titulo: string
  modulo: ModuloErp
  visibilidad: Visibilidad
  etiquetas: string[]
  children?: ReactNode
}) {
  return (
    <article className="h-full rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta transition-colors hover:border-primario/50">
      <div className="flex flex-wrap items-center gap-2">
        <Insignia tono="primario" punto={COLOR_MODULO[modulo]}>{ETIQUETA_MODULO[modulo]}</Insignia>
        {visibilidad === 'INTERNO' && <Insignia tono="aviso">Interno</Insignia>}
      </div>
      <h2 className="mt-2 font-semibold">
        <Link to={`/conocimiento/${id}`} className="hover:text-primario hover:underline">{titulo}</Link>
      </h2>
      {children}
      {etiquetas.length > 0 && (
        <p className="mt-2 flex flex-wrap gap-1">
          {etiquetas.map((e) => (
            <span key={e} className="rounded bg-superficie-2 px-1.5 py-0.5 text-xs text-texto-suave">#{e}</span>
          ))}
        </p>
      )}
    </article>
  )
}
