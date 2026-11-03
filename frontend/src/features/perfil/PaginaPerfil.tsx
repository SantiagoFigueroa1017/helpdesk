import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'

import { api } from '@/api/cliente'
import { ErrorApi, mensajeDeError } from '@/api/errores'
import { Aviso, Boton, CampoTexto, Cargando, Insignia, Tarjeta } from '@/components/ui'
import { cerrarSesion, renovar } from '@/features/auth/sesion'
import { formato } from '@/lib/dominio'

/** UsuarioResponse del backend (2.5). */
type Perfil = {
  id: string
  nombre: string
  email: string
  empresaId: string | null
  roles: { id: string; nombre: string; alcance: string; activo: boolean }[]
  activo: boolean
  fechaCreacion: string
}

const esquema = z
  .object({
    nombre: z.string().trim().min(1, 'Escribe tu nombre.').max(150, 'El nombre admite como máximo 150 caracteres.'),
    passwordActual: z.string(),
    nuevaPassword: z.string(),
    confirmar: z.string(),
  })
  .superRefine((d, ctx) => {
    if (!d.nuevaPassword) return
    if (d.nuevaPassword.length < 8 || d.nuevaPassword.length > 72) {
      ctx.addIssue({ code: 'custom', path: ['nuevaPassword'], message: 'La contraseña debe tener entre 8 y 72 caracteres.' })
    }
    if (!d.passwordActual) {
      ctx.addIssue({ code: 'custom', path: ['passwordActual'], message: 'Escribe tu contraseña actual.' })
    }
    if (d.confirmar !== d.nuevaPassword) {
      ctx.addIssue({ code: 'custom', path: ['confirmar'], message: 'Las contraseñas no coinciden.' })
    }
  })

type Datos = z.infer<typeof esquema>

/** Perfil propio (HU06 y HU07 sin permiso): nombre y contraseña. Cambiar la contraseña cierra todas las sesiones. */
export function PaginaPerfil() {
  const cliente = useQueryClient()
  const [guardado, setGuardado] = useState(false)
  const perfil = useQuery({ queryKey: ['perfil'], queryFn: ({ signal }) => api<Perfil>('/api/perfil', { signal }) })

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<Datos>({
    resolver: zodResolver(esquema),
    values: perfil.data ? { nombre: perfil.data.nombre, passwordActual: '', nuevaPassword: '', confirmar: '' } : undefined,
  })

  const guardar = useMutation({
    mutationFn: (d: Datos) =>
      api<Perfil>('/api/perfil', {
        metodo: 'PUT',
        cuerpo: {
          nombre: d.nombre,
          passwordActual: d.nuevaPassword ? d.passwordActual : null,
          nuevaPassword: d.nuevaPassword || null,
        },
      }),
    onSuccess: async (actualizado, d) => {
      if (d.nuevaPassword) {
        // El backend ya cerró todas las sesiones de este usuario (2.5): se vuelve al login.
        await cerrarSesion('password')
        return
      }
      cliente.setQueryData(['perfil'], actualizado)
      reset({ nombre: actualizado.nombre, passwordActual: '', nuevaPassword: '', confirmar: '' })
      setGuardado(true)
      await renovar() // trae el nombre nuevo a la barra superior
    },
    onError: (e) => {
      if (e instanceof ErrorApi) {
        for (const campo of ['nombre', 'passwordActual', 'nuevaPassword'] as const) {
          const m = e.errorDe(campo)
          if (m) setError(campo, { message: m })
        }
      }
    },
  })

  if (perfil.isPending) return <Cargando />
  if (perfil.isError) return <Aviso tono="peligro">{mensajeDeError(perfil.error)}</Aviso>

  const p = perfil.data
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Mi perfil</h1>
        <p className="mt-1 text-texto-suave">{p.email}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Tarjeta titulo="Datos y contraseña">
          <form
            noValidate
            className="flex flex-col gap-4"
            onSubmit={handleSubmit((d) => {
              setGuardado(false)
              guardar.mutate(d)
            })}
          >
            {guardado && <Aviso tono="exito">Cambios guardados.</Aviso>}
            {guardar.isError && !(guardar.error instanceof ErrorApi && guardar.error.codigo === 'VALIDACION') && (
              <Aviso tono="peligro">{mensajeDeError(guardar.error)}</Aviso>
            )}
            <CampoTexto etiqueta="Nombre" obligatorio autoComplete="name" error={errors.nombre?.message} {...register('nombre')} />

            <fieldset className="flex flex-col gap-4 border-t border-borde pt-4">
              <legend className="pr-2 text-sm font-semibold">Cambiar contraseña (opcional)</legend>
              <CampoTexto
                etiqueta="Contraseña actual"
                type="password"
                autoComplete="current-password"
                error={errors.passwordActual?.message}
                {...register('passwordActual')}
              />
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoTexto
                  etiqueta="Nueva contraseña"
                  type="password"
                  autoComplete="new-password"
                  ayuda="Entre 8 y 72 caracteres."
                  error={errors.nuevaPassword?.message}
                  {...register('nuevaPassword')}
                />
                <CampoTexto
                  etiqueta="Confirmar nueva contraseña"
                  type="password"
                  autoComplete="new-password"
                  error={errors.confirmar?.message}
                  {...register('confirmar')}
                />
              </div>
              <p className="text-xs text-texto-suave">Al cambiarla se cierran tus sesiones en todos los dispositivos.</p>
            </fieldset>

            <div className="flex justify-end">
              <Boton type="submit" cargando={guardar.isPending}>
                Guardar cambios
              </Boton>
            </div>
          </form>
        </Tarjeta>

        <Tarjeta titulo="Tus roles">
          <ul className="flex flex-col gap-2">
            {p.roles.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="font-medium">{r.nombre}</span>
                <Insignia tono={r.activo ? 'primario' : 'neutro'}>{r.alcance === 'GLOBAL' ? 'Global' : 'Empresa'}</Insignia>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-xs text-texto-suave">Usuario desde el {formato.fecha(p.fechaCreacion)}.</p>
        </Tarjeta>
      </div>
    </div>
  )
}
