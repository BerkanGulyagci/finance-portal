import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // Local dev: /api/* → backend localhost:8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        /** İlk EVDS liste cache doldurma uzun sürebilir — varsayılan proxy zaman aşımını aşmaması için */
        timeout: 300_000,
      },
    },
  },
  // Vitest — birim/komponent testleri. `npm run test` (vitest run) ile CI'da koşar.
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.{test,spec}.{js,jsx}'],
    css: false,
  },
})
