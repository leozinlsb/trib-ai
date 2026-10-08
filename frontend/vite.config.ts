import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Em desenvolvimento o front chama /api no próprio Vite, que repassa para o backend.
// TRIBIA_BACKEND_URL só é lido aqui (Node); não vai para o navegador.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.TRIBIA_BACKEND_URL || 'http://localhost:8090'
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
      },
    },
    preview: {
      port: 4173,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
      },
    },
  }
})
