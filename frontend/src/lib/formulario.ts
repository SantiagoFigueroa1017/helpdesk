import { useState, type ChangeEvent } from 'react'

import { ErrorApi, mensajeDeError } from '@/api/errores'

type Control = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement

/**
 * Estado de un formulario de las pantallas de gestión (tareas 3.3 a 3.5). Las reglas las valida
 * el backend (Bean Validation y reglas de negocio): este hook guarda los valores y pinta en cada
 * campo los "errores" del 400 VALIDACION. Los errores de campos que el formulario no muestra
 * quedan en el mensaje general, así ninguno se pierde.
 */
export function useFormulario<T extends Record<string, unknown>>(inicial: T) {
  const [valores, setValores] = useState<T>(inicial)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [general, setGeneral] = useState<string | null>(null)

  const poner = <K extends keyof T>(clave: K, valor: T[K]) => {
    setValores((v) => ({ ...v, [clave]: valor }))
  }

  return {
    valores,
    errores,
    general,
    poner,
    /** Vuelve a los valores dados y borra los errores (al abrir un diálogo). */
    reiniciar: (v: T) => {
      setValores(v)
      setErrores({})
      setGeneral(null)
    },
    limpiar: () => {
      setErrores({})
      setGeneral(null)
    },
    /** Reparte el error de la API entre los campos y el mensaje general. */
    fallo: (e: unknown) => {
      if (e instanceof ErrorApi && e.errores.length > 0) {
        const porCampo: Record<string, string> = {}
        const sueltos: string[] = []
        for (const { campo, mensaje } of e.errores) {
          const raiz = campo.split(/[.[]/)[0]
          if (raiz in valores) porCampo[campo] = mensaje
          else sueltos.push(mensaje)
        }
        setErrores(porCampo)
        setGeneral(sueltos.length ? sueltos.join(' ') : 'Revisa los campos marcados.')
      } else {
        setErrores({})
        setGeneral(mensajeDeError(e))
      }
    },
    /** Props de un campo de texto o lista: value, onChange y error. */
    campo: (clave: keyof T & string) => ({
      name: clave,
      value: valores[clave] == null ? '' : String(valores[clave]),
      onChange: (e: ChangeEvent<Control>) => poner(clave, e.target.value as T[typeof clave]),
      error: errores[clave],
    }),
  }
}

/** Texto vacío a null, para los opcionales del backend. */
export const nulo = (t: string) => (t.trim() === '' ? null : t.trim())

/** Número escrito en un campo; null si está vacío o no es número (el backend responde el error). */
export const numero = (t: string): number | null => {
  if (t.trim() === '') return null
  const n = Number(t.replace(',', '.'))
  return Number.isFinite(n) ? n : null
}
