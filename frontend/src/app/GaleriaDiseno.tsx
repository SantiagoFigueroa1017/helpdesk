import { useState, type ReactNode } from 'react'
import { Moon, Plus, Search, Sun, Trash2 } from 'lucide-react'

import {
  AreaTexto,
  Aviso,
  Boton,
  CampoTexto,
  Cargando,
  Casilla,
  Dialogo,
  Estadistica,
  Insignia,
  Selector,
  Tabla,
  Tarjeta,
  Td,
  Th,
  Tr,
  Vacio,
} from '@/components/ui'
import {
  COLOR_MODULO,
  ESTADO_CAPACITACION,
  ESTADO_MEMBRESIA,
  ESTADO_TICKET,
  ETIQUETA_MODULO,
  MODULOS_ERP,
  formato,
  type EstadoTicket,
  type ModuloErp,
} from '@/lib/dominio'
import { useTema } from '@/lib/tema'

/**
 * Página de muestra del sistema de diseño (tarea 3.1). Reúne los tokens y los componentes base para
 * revisarlos en claro y oscuro. En la 3.2 la reemplazan el login y el layout con menú.
 */
export function GaleriaDiseno() {
  const { tema, alternar } = useTema()
  const [dialogo, setDialogo] = useState(false)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:py-10">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-primario">Helpdesk · Sistemas Integrados</p>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">Sistema de diseño</h1>
          <p className="mt-1 text-texto-suave">Paleta A «Índigo sereno», tipografía Inter y componentes base.</p>
        </div>
        <Boton variante="secundario" onClick={alternar} icono={tema === 'claro' ? <Moon className="size-4" /> : <Sun className="size-4" />}>
          {tema === 'claro' ? 'Tema oscuro' : 'Tema claro'}
        </Boton>
      </header>

      <Tarjeta titulo="Colores" descripcion="Tokens de src/index.css. Se usan como bg-primario, text-texto-suave, border-borde…">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          {MUESTRAS.map((m) => (
            <div key={m.clase} className="overflow-hidden rounded-control border border-borde">
              <div className={`h-14 ${m.clase}`} />
              <div className="px-2.5 py-2">
                <p className="text-xs font-semibold">{m.nombre}</p>
                <p className="font-mono text-[11px] text-texto-suave">{m.clase}</p>
              </div>
            </div>
          ))}
        </div>
      </Tarjeta>

      <div className="grid gap-6 lg:grid-cols-2">
        <Tarjeta titulo="Tipografía">
          <div className="space-y-2">
            <p className="text-3xl font-semibold tracking-tight">Título de página</p>
            <p className="text-xl font-semibold">Título de sección</p>
            <p className="text-base">Texto normal para descripciones y contenido de los tickets.</p>
            <p className="text-sm text-texto-suave">Texto secundario: fechas, ayudas y metadatos.</p>
            <p className="text-sm">
              Enlace <a className="font-semibold text-acento underline-offset-2 hover:underline" href="#colores">
                a un artículo de la base de conocimientos
              </a>
            </p>
            <p className="font-mono text-sm text-texto-suave">Cifras tabulares: {formato.numero(7894)} · {formato.pesos(397800000)}</p>
          </div>
        </Tarjeta>

        <Tarjeta titulo="Botones">
          <div className="flex flex-wrap gap-2">
            <Boton icono={<Plus className="size-4" />}>Nuevo ticket</Boton>
            <Boton variante="secundario">Cancelar</Boton>
            <Boton variante="fantasma" icono={<Search className="size-4" />}>
              Buscar
            </Boton>
            <Boton variante="peligro" icono={<Trash2 className="size-4" />}>
              Eliminar
            </Boton>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <Boton tamano="sm">Pequeño</Boton>
            <Boton cargando>Guardando</Boton>
            <Boton disabled>Desactivado</Boton>
          </div>
        </Tarjeta>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Estadistica etiqueta="Tickets abiertos" valor={formato.numero(48)} detalle="Sin asignar: 12" />
        <Estadistica etiqueta="En proceso" valor={formato.numero(58)} detalle="14 agentes" />
        <Estadistica etiqueta="Recaudo del mes" valor={formato.pesos(33150000)} detalle="3 pagos fallidos" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Tarjeta titulo="Formulario" descripcion="Así se verá el formulario dinámico del ticket (3.3).">
          <form className="flex flex-col gap-4" onSubmit={(e) => e.preventDefault()}>
            <CampoTexto etiqueta="Título" placeholder="Describe el problema en una frase" obligatorio />
            <Selector
              etiqueta="Módulo del ERP"
              obligatorio
              vacio="Selecciona un módulo"
              opciones={MODULOS_ERP.map((m) => ({ valor: m, etiqueta: ETIQUETA_MODULO[m] }))}
            />
            <CampoTexto etiqueta="ID de la pasarela de pago" defaultValue="PAS-" error="Escribe el ID completo de la pasarela." />
            <AreaTexto etiqueta="Descripción" ayuda="Incluye los pasos para reproducir el error." />
            <Casilla etiqueta="Es una sugerencia, no un incidente" />
            <div className="flex justify-end gap-2">
              <Boton variante="secundario">Cancelar</Boton>
              <Boton type="submit">Crear ticket</Boton>
            </div>
          </form>
        </Tarjeta>

        <div className="flex flex-col gap-6">
          <Tarjeta titulo="Insignias">
            <div className="space-y-3">
              <Fila titulo="Ticket">
                {Object.values(ESTADO_TICKET).map((e) => (
                  <Insignia key={e.etiqueta} tono={e.tono}>
                    {e.etiqueta}
                  </Insignia>
                ))}
              </Fila>
              <Fila titulo="Membresía">
                {Object.values(ESTADO_MEMBRESIA).map((e) => (
                  <Insignia key={e.etiqueta} tono={e.tono}>
                    {e.etiqueta}
                  </Insignia>
                ))}
              </Fila>
              <Fila titulo="Capacitación">
                {Object.values(ESTADO_CAPACITACION).map((e) => (
                  <Insignia key={e.etiqueta} tono={e.tono}>
                    {e.etiqueta}
                  </Insignia>
                ))}
              </Fila>
              <Fila titulo="Módulo">
                {MODULOS_ERP.map((m) => (
                  <Insignia key={m} tono="primario" punto={COLOR_MODULO[m]}>
                    {ETIQUETA_MODULO[m]}
                  </Insignia>
                ))}
              </Fila>
            </div>
          </Tarjeta>

          <Tarjeta titulo="Avisos">
            <div className="space-y-3">
              <Aviso tono="peligro" titulo="Soporte bloqueado">
                La membresía de tu empresa está suspendida. Puedes ver tus tickets y tus pagos.
              </Aviso>
              <Aviso tono="aviso">La búsqueda por significado no está disponible; se muestran resultados por palabras.</Aviso>
              <Aviso tono="exito">Ticket creado.</Aviso>
              <Aviso tono="info" acciones={<Boton tamano="sm" onClick={() => setDialogo(true)}>Abrir diálogo</Boton>}>
                Los diálogos usan el elemento &lt;dialog&gt; del navegador.
              </Aviso>
            </div>
          </Tarjeta>
        </div>
      </div>

      <Tarjeta titulo="Tabla" descripcion="En el celular se desplaza de lado." acciones={<Boton tamano="sm">Exportar</Boton>} sinRelleno>
        <Tabla>
          <thead>
            <tr>
              <Th>Ticket</Th>
              <Th>Módulo</Th>
              <Th>Estado</Th>
              <Th>Empresa</Th>
              <Th className="text-right">Creado</Th>
            </tr>
          </thead>
          <tbody>
            {FILAS.map((f) => (
              <Tr key={f.titulo}>
                <Td className="font-medium">{f.titulo}</Td>
                <Td>
                  <Insignia tono="primario" punto={COLOR_MODULO[f.modulo]}>
                    {ETIQUETA_MODULO[f.modulo]}
                  </Insignia>
                </Td>
                <Td>
                  <Insignia tono={ESTADO_TICKET[f.estado].tono}>{ESTADO_TICKET[f.estado].etiqueta}</Insignia>
                </Td>
                <Td className="text-texto-suave">{f.empresa}</Td>
                <Td className="text-right whitespace-nowrap text-texto-suave">{formato.fecha(f.creado)}</Td>
              </Tr>
            ))}
          </tbody>
        </Tabla>
      </Tarjeta>

      <div className="grid gap-6 sm:grid-cols-2">
        <Tarjeta titulo="Cargando">
          <Cargando texto="Cargando tickets…" />
        </Tarjeta>
        <Tarjeta titulo="Sin resultados">
          <Vacio titulo="No hay tickets con esos filtros">Prueba con otro módulo o quita el rango de fechas.</Vacio>
        </Tarjeta>
      </div>

      <Dialogo
        abierto={dialogo}
        onCerrar={() => setDialogo(false)}
        titulo="¿Eliminar el ticket?"
        descripcion="Se borran también sus 3 comentarios y su vínculo con 1 artículo. No se puede deshacer."
        acciones={
          <>
            <Boton variante="secundario" onClick={() => setDialogo(false)}>
              Cancelar
            </Boton>
            <Boton variante="peligro" onClick={() => setDialogo(false)}>
              Eliminar
            </Boton>
          </>
        }
      />
    </div>
  )
}

function Fila({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="w-24 text-xs font-medium text-texto-suave">{titulo}</span>
      {children}
    </div>
  )
}

const MUESTRAS = [
  { nombre: 'Primario', clase: 'bg-primario' },
  { nombre: 'Primario suave', clase: 'bg-primario-suave' },
  { nombre: 'Acento', clase: 'bg-acento' },
  { nombre: 'Fondo', clase: 'bg-fondo' },
  { nombre: 'Superficie', clase: 'bg-superficie' },
  { nombre: 'Borde', clase: 'bg-borde' },
  { nombre: 'Texto', clase: 'bg-texto' },
  { nombre: 'Texto suave', clase: 'bg-texto-suave' },
  { nombre: 'Lateral', clase: 'bg-lateral' },
  { nombre: 'Éxito', clase: 'bg-exito-fondo' },
  { nombre: 'Aviso', clase: 'bg-aviso-fondo' },
  { nombre: 'Peligro', clase: 'bg-peligro' },
]

const FILAS: { titulo: string; modulo: ModuloErp; estado: EstadoTicket; empresa: string; creado: string }[] = [
  { titulo: 'Pago rechazado en la pasarela', modulo: 'VENTAS_ONLINE', estado: 'ABIERTO', empresa: 'Ferretería El Tornillo', creado: '2026-10-30T15:20:00Z' },
  { titulo: 'La alerta de stock no llega', modulo: 'INVENTARIOS', estado: 'EN_PROCESO', empresa: 'Distribuidora Andina', creado: '2026-10-28T13:05:00Z' },
  { titulo: 'El datáfono se desconecta', modulo: 'VENTAS_EN_SITIO', estado: 'RESUELTO', empresa: 'Panadería La Espiga', creado: '2026-10-21T19:45:00Z' },
  { titulo: 'Orden de compra duplicada', modulo: 'COMPRAS', estado: 'CERRADO', empresa: 'Textiles del Norte', creado: '2026-10-12T16:10:00Z' },
  { titulo: 'Costo del lote mal calculado', modulo: 'PRODUCCION', estado: 'ABIERTO', empresa: 'Lácteos Ocaña', creado: '2026-10-05T14:00:00Z' },
]
