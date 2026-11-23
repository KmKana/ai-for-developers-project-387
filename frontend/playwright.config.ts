import { defineConfig } from '@playwright/test';

import { E2E_BACKEND_PORT, E2E_FRONTEND_PORT, backendUrl, frontendUrl } from './e2e/config';

/**
 * Настоящие React и Spring Boot поднимаются Playwright и останавливаются после
 * тестов (в том числе при падении). Ответы API не подменяются: Vite проксирует
 * /api на свежий бэкенд E2E-порта, все запросы идут через реальные серверы.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: frontendUrl,
    // Используем установленный Chrome, чтобы не скачивать браузер Playwright.
    channel: 'chrome',
    headless: true,
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      name: 'Spring Boot (E2E)',
      command: `mvn -B spring-boot:run -Dspring-boot.run.arguments=--server.port=${E2E_BACKEND_PORT}`,
      cwd: '../backend',
      url: `${backendUrl}/api/meeting-types`,
      timeout: 180_000,
      reuseExistingServer: false,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      name: 'Vite (E2E)',
      command: `npm run dev -- --port ${E2E_FRONTEND_PORT} --strictPort`,
      cwd: '.',
      url: `${frontendUrl}/catalog`,
      timeout: 120_000,
      reuseExistingServer: false,
      env: { VITE_API_PROXY_TARGET: backendUrl },
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
});
