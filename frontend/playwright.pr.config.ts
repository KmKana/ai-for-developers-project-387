import { defineConfig } from '@playwright/test';

import base from './playwright.config';

/**
 * Конфигурация для независимой браузерной проверки PR.
 *
 * Отличается от локального `playwright.config.ts` только диагностикой:
 * trace пишется всегда, а HTML-отчёт и test-results сохраняются как артефакты
 * GitHub Actions. Серверы и порты берутся из базовой конфигурации, поэтому
 * проверка идёт по тем же реальным React и Spring Boot, что и `mise run check`.
 */
export default defineConfig({
  ...base,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    ...(base.use ?? {}),
    trace: 'on',
    screenshot: 'only-on-failure',
  },
});
