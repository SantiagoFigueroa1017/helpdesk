# Funcionalidades

Una carpeta por módulo del backend (contexto maestro 8.1). Cada una se crea con su primera pantalla.

| Carpeta | Backend | Tarea |
|---|---|---|
| `auth/` | `auth`, `/api/perfil` | 3.2 |
| `tickets/` | `tickets` (incluye comentarios y formulario dinámico) | 3.3 |
| `conocimiento/` | `conocimiento` (artículos y búsquedas) | 3.4 |
| `roles/`, `empresas/`, `usuarios/`, `planes/`, `membresias/`, `capacitaciones/` | módulo del mismo nombre | 3.5 |
| `reportes/` | `reportes` (A1 a A9) | 3.6 |

Dentro de cada carpeta: `api.ts` (llamadas con el cliente de `src/api/`), `tipos.ts` (DTO del backend),
`componentes/` y las páginas. Lo compartido vive en `src/components/ui/` (sistema de diseño) y `src/lib/`.
