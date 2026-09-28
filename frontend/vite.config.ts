import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// In development the browser only talks to Vite (same origin). Vite forwards /api
// to Spring Boot, so no CORS configuration is needed (ARCH-SEC-002).
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backendUrl, changeOrigin: false },
    },
  },
  // Lightweight unit/component tests (npm test). jsdom only for tests; the app is unaffected.
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
