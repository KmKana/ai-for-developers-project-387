import react from '@vitejs/plugin-react';
import { configDefaults, defineConfig } from 'vitest/config';

// В dev-режиме запросы /api проксируются на бэкенд (порт 8080).
// E2E поднимает свой бэкенд на отдельном порту и задаёт цель через env.
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxyTarget,
    },
  },
  test: {
    environment: 'jsdom',
    // Playwright-спеки запускаются отдельной командой, Vitest их не собирает.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
});
