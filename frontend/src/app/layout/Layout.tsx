import { useEffect, useRef, useState } from 'react'
import { Headset, LogOut, Menu, Moon, Sun, UserRound, X } from 'lucide-react'
import { Link, Outlet, useLocation } from 'react-router'

import { cerrarSesion } from '@/features/auth/sesion'
import type { UsuarioSesion } from '@/features/auth/permisos'
import { useTema } from '@/lib/tema'

import { MenuLateral } from './MenuLateral'

/**
 * Estructura de las páginas con sesión: menú lateral fijo en escritorio (lg) y desplegable en el
 * celular, barra superior con el usuario, el tema y salir, y el contenido en <Outlet />.
 */
export function Layout({ usuario }: { usuario: UsuarioSesion }) {
  const [menuAbierto, setMenuAbierto] = useState(false)
  const ubicacion = useLocation()
  const principal = useRef<HTMLElement>(null)

  // Al cambiar de página lleva el foco al contenido (lectores de pantalla). El menú del celular se
  // cierra en el clic de cada enlace (alNavegar).
  useEffect(() => {
    principal.current?.focus({ preventScroll: true })
  }, [ubicacion.pathname])

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
      <a
        href="#contenido"
        className="sr-only z-50 rounded-control bg-primario px-3 py-2 text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        Saltar al contenido
      </a>

      {/* Menú lateral de escritorio */}
      <aside className="sticky top-0 hidden h-dvh flex-col overflow-y-auto bg-lateral lg:flex">
        <Marca />
        <MenuLateral usuario={usuario} />
      </aside>

      {/* Menú desplegable del celular */}
      {menuAbierto && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menú">
          <button type="button" aria-label="Cerrar menú" className="absolute inset-0 bg-black/45" onClick={() => setMenuAbierto(false)} />
          <aside className="relative flex h-full w-72 max-w-[85%] flex-col overflow-y-auto bg-lateral">
            <div className="flex items-center justify-between pr-2">
              <Marca />
              <button
                type="button"
                onClick={() => setMenuAbierto(false)}
                aria-label="Cerrar menú"
                className="cursor-pointer rounded-control p-2 text-lateral-texto hover:text-white"
              >
                <X aria-hidden className="size-5" />
              </button>
            </div>
            <MenuLateral usuario={usuario} alNavegar={() => setMenuAbierto(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-col">
        <BarraSuperior usuario={usuario} alAbrirMenu={() => setMenuAbierto(true)} />
        <main id="contenido" ref={principal} tabIndex={-1} className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

function Marca() {
  return (
    <Link to="/" className="flex items-center gap-2.5 px-5 py-5">
      <span className="flex size-8 items-center justify-center rounded-control bg-primario text-white">
        <Headset aria-hidden className="size-4.5" />
      </span>
      <span className="text-sm font-semibold text-white">
        Helpdesk <span className="block text-xs font-normal text-lateral-texto">Sistemas Integrados</span>
      </span>
    </Link>
  )
}

function BarraSuperior({ usuario, alAbrirMenu }: { usuario: UsuarioSesion; alAbrirMenu: () => void }) {
  const { tema, alternar } = useTema()
  const [saliendo, setSaliendo] = useState(false)
  const iniciales = usuario.nombre
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-borde bg-superficie/90 px-3 backdrop-blur sm:px-6">
      <button
        type="button"
        onClick={alAbrirMenu}
        aria-label="Abrir menú"
        className="cursor-pointer rounded-control p-2 text-texto-suave hover:bg-superficie-2 hover:text-texto lg:hidden"
      >
        <Menu aria-hidden className="size-5" />
      </button>

      <div className="ml-auto flex items-center gap-1">
        <button
          type="button"
          onClick={alternar}
          aria-label={tema === 'claro' ? 'Cambiar a tema oscuro' : 'Cambiar a tema claro'}
          className="cursor-pointer rounded-control p-2 text-texto-suave hover:bg-superficie-2 hover:text-texto"
        >
          {tema === 'claro' ? <Moon aria-hidden className="size-4.5" /> : <Sun aria-hidden className="size-4.5" />}
        </button>

        <Link
          to="/perfil"
          className="flex items-center gap-2 rounded-control px-2 py-1.5 hover:bg-superficie-2"
          title="Mi perfil"
        >
          <span aria-hidden className="flex size-8 items-center justify-center rounded-full bg-primario-suave text-xs font-semibold text-sobre-primario-suave">
            {iniciales || <UserRound className="size-4" />}
          </span>
          <span className="hidden text-left sm:block">
            <span className="block text-sm leading-tight font-medium text-texto">{usuario.nombre}</span>
            <span className="block text-xs leading-tight text-texto-suave">{usuario.email}</span>
          </span>
        </Link>

        <button
          type="button"
          disabled={saliendo}
          onClick={async () => {
            setSaliendo(true)
            await cerrarSesion()
          }}
          aria-label="Cerrar sesión"
          title="Cerrar sesión"
          className="cursor-pointer rounded-control p-2 text-texto-suave hover:bg-superficie-2 hover:text-texto disabled:opacity-50"
        >
          <LogOut aria-hidden className="size-4.5" />
        </button>
      </div>
    </header>
  )
}
