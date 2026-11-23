# AGENTS.md

Проект «Запись на звонок» — упрощённый сервис брони встреч по мотивам Cal.com.
Правила записи, ограничения приложения и сценарии описаны в `README.md`.

## Команды (окружение через mise: https://mise.jdx.dev)

- `mise run check` — все проверки: линтеры (Spotless для Java, ESLint + Prettier для фронтенда), тесты бэкенда (JUnit 5), тесты фронтенда (Vitest) и сборка фронтенда с проверкой типов. В CI проверки запускаются через `.github/workflows/hexlet-check.yml` (`hexlet/project-action`; файл Hexlet, не редактировать).
- `mise run lint` — только линтеры.
- `mise run test` — только тесты бэкенда.
- `mise run install` — установка зависимостей бэкенда и фронтенда (перед этим раз `mise install`).
- `mise run backend` / `mise run frontend` — dev-серверы (бэкенд :8080, фронтенд :5173); те же процессы можно держать фоном через daemons (`mise daemons start|stop|status`).
- `mise run hooks` — включить pre-commit (прогоняет `mise run lint`), скрипты в `.githooks/`.
- `npm run generate` (в `api/tsp`) — TypeSpec → OpenAPI: `api/openapi/openapi.yaml`; затем `npm run generate-sdk` (в `frontend/`) — OpenAPI → SDK: `frontend/src/sdk`.

## Устройство

- `api/tsp/main.tsp` — контракт API на TypeSpec. `api/openapi/openapi.yaml` — сгенерированная из него OpenAPI-спецификация, руками не редактировать (`npm run generate` в `api/tsp`).
- `frontend/src/sdk` — сгенерированный из OpenAPI клиентский SDK, руками не редактировать (`npm run generate-sdk` в `frontend/`); линтеры и Prettier его игнорируют.
- `backend/` — Spring Boot 3, Java 21; данные в памяти процесса, перезапуск сбрасывает типы встреч и записи.
- `frontend/` — React 18 + TypeScript + Vite; в dev запросы `/api` проксируются на бэкенд.

## Правила

- Коммиты по Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:` и т.д. — короткое описание в императиве. Каждый коммит реализации должен соответствовать формату Conventional Commits и содержать номер GitHub Issue, к которому относится изменение.
  - `feat: implement booking creation (#16)`
  - `fix: prevent overlapping bookings (#18)`
  - `test: add concurrency tests (#19)`
- Контракт меняется только через TypeSpec: правим `main.tsp`, регенерируем OpenAPI и SDK, бэкенд правим под контракт.
- React-код обращается к API только через сгенерированный SDK из `frontend/src/sdk`: компоненты и хуки не должны дублировать URL и типы ответов вручную, прямые `fetch`/`axios` к `/api` не использовать.
- Бронь слота атомарна (`InMemoryStore.tryAddBooking` под монитором): не ослаблять синхронизацию; тесты на одновременные запросы (`BookingConcurrencyTest`) должны оставаться зелёными.
- Правила записи владельца (часовой пояс, рабочие часы, окно 14 дней, сетка 30 минут) зафиксированы в `BookingRules` и в описании контракта — менять согласованно в обоих местах.

## Agent skills

### Issue tracker

Задачи и спеки хранятся в GitHub Issues репозитория `KmKana/ai-for-developers-project-387` (через `gh`). См. `docs/agents/issue-tracker.md`.

Исторические ссылки остаются в предыдущем репозитории `KmKana/ai-for-developers-project-386`: [спецификация #12](https://github.com/KmKana/ai-for-developers-project-386/issues/12), [карта решений #1](https://github.com/KmKana/ai-for-developers-project-386/issues/1) и исходные ADR. Новые Issue создаются только в `KmKana/ai-for-developers-project-387`.

### Triage labels

Используются стандартные пять меток: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. См. `docs/agents/triage-labels.md`.

### Domain docs

Один контекст: `GLOSSARY.md` и `docs/adr/` в корне репозитория. См. `docs/agents/domain.md`.
