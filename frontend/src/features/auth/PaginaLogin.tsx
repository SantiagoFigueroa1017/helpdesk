import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, Headset, LogIn } from 'lucide-react'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'

import { ErrorApi, mensajeDeError } from '@/api/errores'
import { Aviso, Boton, CampoTexto } from '@/components/ui'

import { iniciarSesion, useSesion } from './sesion'

const esquema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Escribe tu correo.')
    .max(254, 'El correo admite como máximo 254 caracteres.')
    .email('El correo no tiene un formato válido.'),
  password: z.string().min(1, 'Escribe tu contraseña.').max(72, 'La contraseña admite como máximo 72 caracteres.'),
})

type Datos = z.infer<typeof esquema>

/** Traduce los códigos de error del login (acta 2.4) a un mensaje para el usuario. */
function mensajeLogin(e: unknown): string {
  if (!(e instanceof ErrorApi)) return mensajeDeError(e)
  switch (e.codigo) {
    case 'CREDENCIALES_INVALIDAS':
      return 'El correo o la contraseña no son correctos.'
    case 'CUENTA_INACTIVA':
      return 'Tu cuenta está desactivada. Habla con el administrador de tu empresa.'
    case 'DEMASIADOS_INTENTOS': {
      const minutos = Math.max(1, Math.ceil((e.reintentarEn ?? 900) / 60))
      return `Demasiados intentos fallidos. Intenta de nuevo en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`
    }
    default:
      return e.message
  }
}

const MOTIVOS: Record<string, string> = {
  expirada: 'Tu sesión terminó. Vuelve a iniciar sesión para continuar.',
  password: 'Cambiaste tu contraseña: inicia sesión con la nueva.',
}

/** Inicio de sesión (contexto 8.3). Pantalla dividida en escritorio; solo el formulario en el celular. */
export function PaginaLogin() {
  const sesion = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [busqueda] = useSearchParams()
  const [ver, setVer] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError: errorDeCampo,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: { email: '', password: '' } })

  const volver = seguro(busqueda.get('volver'))

  if (sesion.estado === 'activa') return <Navigate to={volver} replace />

  const motivo = sesion.estado === 'anonima' && sesion.motivo ? MOTIVOS[sesion.motivo] : undefined

  const enviar = handleSubmit(async (datos) => {
    setError(null)
    try {
      await iniciarSesion(datos.email.toLowerCase(), datos.password)
      navegar(volver, { replace: true, state: ubicacion.state })
    } catch (e) {
      if (e instanceof ErrorApi && e.codigo === 'VALIDACION') {
        for (const campo of ['email', 'password'] as const) {
          const m = e.errorDe(campo)
          if (m) errorDeCampo(campo, { message: m })
        }
      }
      setError(mensajeLogin(e))
    }
  })

  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-lateral p-12 text-lateral-texto lg:flex lg:flex-col lg:justify-between">
        <Marca />
        <div className="max-w-md">
          <h1 className="text-4xl leading-tight font-semibold tracking-tight text-white">
            Soporte y éxito del cliente para tu ERP
          </h1>
          <p className="mt-4 text-base leading-relaxed">
            Reporta incidentes por módulo, encuentra soluciones en la base de conocimientos y aprovecha las capacitaciones
            de tu plan.
          </p>
        </div>
        <p className="text-sm">Sistemas Integrados S.A. · Proyecto de aula UFPSO</p>
        <div aria-hidden className="pointer-events-none absolute -right-24 -bottom-24 size-96 rounded-full bg-primario/30 blur-3xl" />
      </aside>

      <main className="flex items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Marca oscura />
          </div>
          <h2 className="text-2xl font-semibold tracking-tight">Inicia sesión</h2>
          <p className="mt-1 text-sm text-texto-suave">Usa el correo con el que te registraron.</p>

          <form onSubmit={enviar} noValidate className="mt-6 flex flex-col gap-4">
            {motivo && !error && <Aviso tono="info">{motivo}</Aviso>}
            {error && <Aviso tono="peligro">{error}</Aviso>}

            <CampoTexto
              etiqueta="Correo"
              type="email"
              autoComplete="username"
              inputMode="email"
              autoFocus
              error={errors.email?.message}
              {...register('email')}
            />
            <div className="relative">
              <CampoTexto
                etiqueta="Contraseña"
                type={ver ? 'text' : 'password'}
                autoComplete="current-password"
                className="pr-11"
                error={errors.password?.message}
                {...register('password')}
              />
              <button
                type="button"
                onClick={() => setVer((v) => !v)}
                aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                aria-pressed={ver}
                className="absolute top-8 right-1.5 cursor-pointer rounded-control p-1.5 text-texto-suave hover:text-texto"
              >
                {ver ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
              </button>
            </div>

            <Boton type="submit" cargando={isSubmitting} icono={<LogIn aria-hidden className="size-4" />} className="mt-2 w-full">
              Entrar
            </Boton>
          </form>

          <p className="mt-6 text-xs text-texto-suave">
            ¿Olvidaste tu contraseña? Pídele al administrador de tu empresa que te asigne una nueva.
          </p>
        </div>
      </main>
    </div>
  )
}

function Marca({ oscura = false }: { oscura?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex size-9 items-center justify-center rounded-control bg-primario text-white">
        <Headset aria-hidden className="size-5" />
      </span>
      <span className={oscura ? 'font-semibold text-texto' : 'font-semibold text-white'}>
        Helpdesk <span className="font-normal opacity-75">· Sistemas Integrados</span>
      </span>
    </div>
  )
}

/** Solo rutas internas: evita que ?volver=https://otro-sitio lleve fuera de la aplicación. */
function seguro(ruta: string | null): string {
  return ruta && ruta.startsWith('/') && !ruta.startsWith('//') ? ruta : '/'
}
