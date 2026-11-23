# Scheduled Lighthouse audit

Workflow `.github/workflows/scheduled-audit.yml` раз в сутки проверяет публичное
приложение через Lighthouse, сохраняет отчёт, анализирует его через OpenCode и
создаёт **один** GitHub Issue по новой находке. Повторный запуск не создаёт
дубликат.

## Расписание и режимы

- Расписание: ежедневно в **09:17 Asia/Almaty** (`cron: "17 9 * * *"`,
  `timezone: "Asia/Almaty"`).
- Ручной запуск: `workflow_dispatch`.
- `demo_finding=true` — использовать фиктивный воспроизводимый отчёт
  `scripts/scheduled-audit/fixtures/demo-lighthouse.json`. Issue получает
  `[DEMO]` в заголовке и предупреждение в теле; это учебная находка, а не
  реальный дефект.
- `demo_finding=false` — живой Lighthouse по `APP_URL`.
- Demo-данные **никогда** не используются в schedule-режиме.

## APP_URL

URL не хардкодится. Workflow читает GitHub Actions Variable `APP_URL`:

```bash
gh variable set APP_URL --repo KmKana/ai-for-developers-project-387 --body "https://<current-public-app>"
```

или **Settings → Secrets and variables → Actions → Variables → New repository
variable**. Значение должно быть текущей публичной сборкой этого репозитория.
Старый Render-адрес `callbooking-uklz.onrender.com` обслуживает старую сборку
проекта-386 (без темы разговора) и не должен использоваться как APP_URL.

Если `APP_URL` не задан или страница недоступна, workflow фиксирует **NO_DATA**,
не вызывает модель, не создаёт Issue и завершает job с ошибкой. Показатели
Lighthouse не выдумываются.

## Отчёты и артефакты

Workflow пишет всё в `scheduled-audit-output/` (каталог в `.gitignore`):

- `lighthouse.json`, `lighthouse.html` — сырой и HTML-отчёт;
- `summary.json` — компактные категории, падающие аудиты и primary finding;
- `issues.json`, `issues-summary.json` — снимок Issues для дедупликации;
- `decision.json` — fingerprint и статус NEW/DUPLICATE_SKIPPED/NO_FINDING;
- `agent-output.txt`, `prompt.txt`, `issue/issue-title.txt`,
  `issue/issue-body.md`, `status.json`.

Всё это загружается как artifact `scheduled-audit-<run_id>` (retention 14 дней).
В Git отчёты не попадают; workflow проверяет, что tracked-файлы не изменились.

## Fingerprint и дедупликация

Fingerprint строится из:

```text
normalized APP_URL | категория Lighthouse | audit id (правило/метрика)
```

Значение метрики не входит: ухудшение той же проблемы не создаёт новый Issue.
Значимой считается находка со score ниже `0.9`; более мелкие аудиты не создают
Issue и не вызывают модель. Маркер `<!-- scheduled-audit-fingerprint: sa-v1-… -->`
записывается в тело Issue. Перед моделью и повторно перед созданием Issue workflow читает **все**
открытые и закрытые Issues и ищет маркер.

- Найдено — `DUPLICATE_SKIPPED`, модель не вызывается (или второй раз не
  создаётся Issue), ссылка на существующий Issue попадает в summary; новых
  комментариев нет.
- Не найдено — модель анализирует отчёт; при `FINDING` workflow создаёт Issue.
- Модель вернула `NO_FINDING` — Issue не создаётся, статус `NO_FINDING`.

Demo fingerprint стабилен и отделён от live-находок, поэтому второй запуск с тем
же fixture даёт `DUPLICATE_SKIPPED`.

## Анализ OpenCode

- Модель: `opencode/deepseek-v4.1-flash` (та же экономичная модель, что у
  triage/ревью).
- Ключ: `OPENCODE_API_KEY` или `SECRETPAY`; значение не печатается.
- Конфиг: `opencode.audit.json`, агент `auditor`, только чтение: запрещены
  `edit`, `bash`, `task`, сеть, навыки, вопросы и внешние каталоги.
- Модель **не получает** `GH_TOKEN` и не создаёт Issue/PR. Issue создаёт
  workflow с правом `issues: write`.
- Модель вызывается только если есть данные, есть primary finding и fingerprint
  не найден среди Issues. При `NO_DATA`, `NO_FINDING`, `DUPLICATE_SKIPPED` и
  ошибке Lighthouse модель не вызывается.
- Таймаут модели — 300 секунд; job — 20 минут; один Issue на находку.

## Ручные демонстрационные запуски

После merge workflow в `main`:

```bash
# 1. Первый запуск: создаёт [DEMO] Issue
gh workflow run scheduled-audit.yml \
  --repo KmKana/ai-for-developers-project-387 \
  --ref main \
  -f demo_finding=true

# 2. Дождаться завершения и найти Issue
gh run list --repo KmKana/ai-for-developers-project-387 \
  --workflow scheduled-audit.yml --limit 5

# 3. Второй запуск с теми же данными: должен дать DUPLICATE_SKIPPED
gh workflow run scheduled-audit.yml \
  --repo KmKana/ai-for-developers-project-387 \
  --ref main \
  -f demo_finding=true
```

Второй запуск не должен создавать новый Issue, не должен публиковать
комментарий и должен сохранить ссылку на первый Issue в summary/artifact.

## Статусы

| Статус | Значение |
|---|---|
| `ISSUE_CREATED` | Новая находка, Issue создан |
| `DUPLICATE_SKIPPED` | Fingerprint уже есть в открытом/закрытом Issue; модель и Issue пропущены |
| `NO_FINDING` | Нет подходящей новой находки; Issue не создаётся |
| `NO_DATA` | Нет APP_URL или приложение/Lighthouse недоступны; модель не вызывается |
| `AGENT_FAILED` | Ошибка/таймаут модели или парсинга; Issue не создаётся |
| `ISSUE_FAILED` | Не удалось создать Issue |
| `DEMO` | Использован фиктивный fixture; Issue помечен `[DEMO]` |

`NO_DATA` и ошибки явно отражаются в summary и не считаются успешным аудитом.

## Безопасность и ограничения

- `permissions: contents: read, issues: write`; никаких секретов в отчётах.
- Checkout `main` с `persist-credentials: false`.
- Concurrency `scheduled-audit`, `cancel-in-progress: false`: два аудита не идут
  одновременно и не отменяют друг друга.
- Concurrency не использует `${{ github.event.comment.user.id }}`; этот workflow
  не слушает комментарии.
- Лимит расходов: один вызов модели на новую находку, не более; дедупликация
  включает закрытые Issues; `NO_DATA`/`DUPLICATE_SKIPPED`/`NO_FINDING` бесплатны.
  Автопополнение баланса не включается.
