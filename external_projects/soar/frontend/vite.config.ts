import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Domínios servidos pelo Caddy do meada (dev .local / prod .com).
const allowedHosts = ['.meadadigital.local', '.meadadigital.com']

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3130,
    allowedHosts,
  },
  preview: {
    port: 3130,
    allowedHosts,
  },
})
