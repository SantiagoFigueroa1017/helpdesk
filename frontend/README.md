# Frontend · Helpdesk & Customer Success

React 19 + Vite 8 + Tailwind CSS 4 + TypeScript 6 (tarea 3.1).

## Correr en desarrollo

```powershell
cd frontend
npm install
npm run dev
```

Abre `http://localhost:5173`. Las peticiones a `/api` van al backend en `http://localhost:8080`
(proxy de Vite en `vite.config.ts`), así que el backend debe estar corriendo para las pantallas con datos.

Otros comandos: `npm run build` (revisa tipos y genera `dist/`), `npm run lint`.

## Estructura

```
src/
  app/              raíz de la aplicación (App, y desde la 3.2 router y proveedores)
  components/ui/    sistema de diseño: Boton, CampoTexto, Selector, Insignia, Tarjeta, Tabla, Dialogo, Aviso…
  features/         una carpeta por módulo del backend (ver features/README.md)
  lib/              utilidades: dominio (enums y etiquetas), tema claro/oscuro, cn
  index.css         tokens de diseño (paleta A «Índigo sereno») para Tailwind v4
```

## Sistema de diseño

- Los colores se usan por su nombre: `bg-primario`, `text-texto-suave`, `border-borde`, `bg-exito-fondo`…
  Están en `src/index.css`, en claro y oscuro. No uses colores sueltos de Tailwind (`bg-indigo-700`).
- Todas las parejas de texto y fondo cumplen WCAG AA.
- Importa los componentes desde `@/components/ui`. El alias `@/` apunta a `src/`.
