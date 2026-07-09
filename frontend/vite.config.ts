/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const BACKEND = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // API traffic lives under /api so SPA routes (e.g. /teams, /epics) never
    // collide with backend paths and survive a hard refresh. The prefix is
    // stripped before forwarding because the backend serves at root.
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Tests hit the mocked backend directly (MSW handlers use root paths), so
    // the browser-only /api prefix must be disabled here.
    env: { VITE_API_BASE: '' },
  },
})
