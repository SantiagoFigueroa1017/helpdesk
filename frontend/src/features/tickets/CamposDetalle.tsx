import { AreaTexto, CampoTexto, Selector } from '@/components/ui'

import type { CampoModulo } from './tipos'

/**
 * Formulario dinámico del detalle de un incidente (HU21): los campos salen de GET /api/tickets/campos
 * según el módulo elegido, con las mismas reglas del validador $jsonSchema. Los errores llegan del
 * backend como "detalle.<nombre>".
 */
export function CamposDetalle({
  campos,
  valores,
  onCambiar,
  errores,
}: {
  campos: CampoModulo[]
  valores: Record<string, string>
  onCambiar: (nombre: string, valor: string) => void
  errores: Record<string, string>
}) {
  return (
    <fieldset className="grid gap-4 rounded-control border border-borde p-4 sm:grid-cols-2">
      <legend className="px-1 text-sm font-semibold">Datos del módulo</legend>
      {campos.map((c) => {
        const comun = {
          etiqueta: c.etiqueta,
          obligatorio: c.obligatorio,
          value: valores[c.nombre] ?? '',
          error: errores[`detalle.${c.nombre}`],
        }
        if (c.tipo === 'OPCION') {
          return (
            <Selector
              key={c.nombre}
              {...comun}
              vacio="Selecciona una opción"
              opciones={(c.opciones ?? []).map((o) => ({ valor: o.valor, etiqueta: o.etiqueta }))}
              onChange={(e) => onCambiar(c.nombre, e.target.value)}
            />
          )
        }
        if (c.tipo === 'TEXTO_LARGO') {
          return (
            <div key={c.nombre} className="sm:col-span-2">
              <AreaTexto {...comun} rows={3} maxLength={c.longitudMaxima ?? undefined} onChange={(e) => onCambiar(c.nombre, e.target.value)} />
            </div>
          )
        }
        const numerico = c.tipo !== 'TEXTO'
        return (
          <CampoTexto
            key={c.nombre}
            {...comun}
            inputMode={c.tipo === 'ENTERO' ? 'numeric' : numerico ? 'decimal' : undefined}
            maxLength={c.longitudMaxima ?? undefined}
            ayuda={c.tipo === 'DINERO' ? 'En pesos, sin puntos de miles.' : undefined}
            onChange={(e) => onCambiar(c.nombre, e.target.value)}
          />
        )
      })}
    </fieldset>
  )
}
