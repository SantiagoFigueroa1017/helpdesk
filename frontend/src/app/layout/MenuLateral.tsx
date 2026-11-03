import { NavLink } from 'react-router'

import { tieneAlguno, type UsuarioSesion } from '@/features/auth/permisos'
import { cn } from '@/lib/cn'

import { INICIO, MENU, type EntradaMenu } from '../navegacion'

/** Menú lateral: solo aparecen las secciones y entradas que los permisos del usuario habilitan. */
export function MenuLateral({ usuario, alNavegar }: { usuario: UsuarioSesion; alNavegar?: () => void }) {
  const secciones = MENU.map((s) => ({ ...s, entradas: s.entradas.filter((e) => tieneAlguno(usuario, e.permisos)) })).filter(
    (s) => s.entradas.length > 0,
  )
  const deEmpresa = usuario.alcance === 'EMPRESA'

  return (
    <nav aria-label="Menú principal" className="flex flex-col gap-5 px-3 py-4">
      <ul>
        <Entrada entrada={INICIO} deEmpresa={deEmpresa} alNavegar={alNavegar} />
      </ul>
      {secciones.map((s) => (
        <div key={s.titulo}>
          <p className="px-3 pb-1.5 text-[11px] font-semibold tracking-wider text-lateral-texto/60 uppercase">{s.titulo}</p>
          <ul className="flex flex-col gap-0.5">
            {s.entradas.map((e) => (
              <Entrada key={e.ruta} entrada={e} deEmpresa={deEmpresa} alNavegar={alNavegar} />
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

function Entrada({ entrada, deEmpresa, alNavegar }: { entrada: EntradaMenu; deEmpresa: boolean; alNavegar?: () => void }) {
  const Icono = entrada.icono
  return (
    <li>
      <NavLink
        to={entrada.ruta}
        end={entrada.ruta === '/'}
        onClick={alNavegar}
        className={({ isActive }) =>
          cn(
            'flex items-center gap-3 rounded-control px-3 py-2 text-sm transition-colors',
            isActive ? 'bg-lateral-activo font-semibold text-white' : 'text-lateral-texto hover:bg-lateral-activo/60 hover:text-white',
          )
        }
      >
        <Icono aria-hidden className="size-4 shrink-0" />
        {(deEmpresa && entrada.etiquetaEmpresa) || entrada.etiqueta}
      </NavLink>
    </li>
  )
}
