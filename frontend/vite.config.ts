import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Configuración de Vite (tarea 3.1).
// - Tailwind v4 entra como plugin de Vite; no hay tailwind.config.js: los tokens viven en src/index.css.
// - "@/..." apunta a src/ (el mismo alias está en tsconfig.app.json para el editor).
// - En desarrollo, /api va al backend en el puerto 8080. Frontend y backend comparten origen, así la
//   cookie del refresh token (Path=/api/auth, SameSite=Strict) funciona sin CORS (contexto 8.3 y 9).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
})
