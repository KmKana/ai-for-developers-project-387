# Календарь звонков (продолжение)

[![hexlet-check](https://github.com/KmKana/ai-for-developers-project-387/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/KmKana/ai-for-developers-project-387/actions)

Упрощённый сервис бронирования встреч по мотивам [Cal.com](https://cal.com): владелец публикует типы встреч и доступное время, гость выбирает свободный слот и записывается на звонок. Есть каталог типов встреч, календарь на 14 дней, сетка 30 минут, список предстоящих встреч владельца и защита от двойной записи в один слот.

В проекте нет авторизации, личных кабинетов, внешних календарей и уведомлений — функциональность намеренно ограничена.

## Репозитории и история

- Новый репозиторий: <https://github.com/KmKana/ai-for-developers-project-387>
- Исходный проект (первый этап): <https://github.com/KmKana/ai-for-developers-project-386>
- Учебная программа Хекслета: <https://ru.hexlet.io/programs/ai-for-developers>
- Требования первого этапа: <https://files.hexlet.app/a/2ipc5m>

Исторические решения остаются в исходном репозитории и не переносятся в новый трекер:

- [спецификация #12](https://github.com/KmKana/ai-for-developers-project-386/issues/12) — техническая спецификация сервиса бронирования;
- [карта решений #1](https://github.com/KmKana/ai-for-developers-project-386/issues/1) — карта решений по спецификации;
- [ADR 0001](docs/adr/0001-home-page-scope.md) — решение о статичной главной странице.

Новые задачи создаются в Issues репозитория `KmKana/ai-for-developers-project-387` (см. `AGENTS.md` и `docs/agents/issue-tracker.md`).

## Стек

- Бэкенд: Java 21, Spring Boot 3, Maven; данные живут в памяти процесса.
- Фронтенд: React 18, TypeScript, Vite.
- Контракт API: TypeSpec (`api/tsp/main.tsp`) → OpenAPI (`api/openapi/openapi.yaml`) → SDK (`frontend/src/sdk`).
- Инструменты зафиксированы в `mise.toml`: Java 21, Maven 3.9.6, Node 24.22.3.

## Установка

```bash
git clone https://github.com/KmKana/ai-for-developers-project-387.git
cd ai-for-developers-project-387
mise install
mise run install
```

`mise run install` скачивает зависимости Maven, TypeSpec и фронтенда из lockfile. Без mise:

```bash
cd api/tsp && npm ci
cd ../../frontend && npm ci
cd ../backend && ./mvnw -B -DskipTests package
```

Переменные окружения и секреты для локального запуска не нужны; файлы `.env` в репозиторий не добавляются.

## Локальный запуск

В двух терминалах из корня проекта:

```bash
mise run backend     # Spring Boot на http://localhost:8080
mise run frontend    # Vite на http://localhost:5173, /api проксируется на :8080
```

Откройте <http://localhost:5173>. Без mise:

```bash
cd backend && ./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
```

```bash
cd frontend && npm run dev
```

## Тесты

Единый шлюз (тот же набор, что в CI):

```bash
mise run check
```

Он включает Spotless, ESLint/Prettier, JUnit 5 (включая контрактные тесты и `BookingConcurrencyTest`), Vitest, проверку типов и сборку React, проверку расхождений OpenAPI/SDK, тесты самого шлюза и release-please, а затем браузерный Playwright E2E. Отдельно:

```bash
cd backend && ./mvnw test        # JUnit 5
cd frontend && npm test          # Vitest
cd frontend && npm run test:e2e  # Playwright: поднимает реальные серверы на :18080 и :15173
```

Для E2E нужен установленный Google Chrome (`channel: 'chrome'`); в CI используется `ubuntu-24.04`, где он предустановлен. При изменении контракта его нужно перегенерировать:

```bash
cd api/tsp && npm run generate              # TypeSpec -> OpenAPI
cd ../../frontend && npm run generate-sdk   # OpenAPI -> SDK
```

## OpenCode в GitHub Actions

Workflow `.github/workflows/opencode.yml` использует локальную копию [действия OpenCode для GitHub](https://github.com/anomalyco/opencode/blob/latest/github/README.md) из `.github/actions/opencode-pinned`, созданную по commit `77fc88c8ade8e5a620ebbe1197f3a572d29ae91a`. CLI закреплён на версии `1.19.6`: установка выполняется с `--version 1.19.6` и проверкой `opencode --version`, кэш upstream-экшна по «последнему релизу» не используется. Он срабатывает только на события `issue_comment` и `pull_request_review_comment` типа `created`, когда комментарий содержит `/oc` или `/opencode` и оставлен пользователем `KmKana`; комментарии ботов игнорируются. Авторизация самого действия по-прежнему идёт через GitHub App (`id-token: write`, `contents: read`), checkout выполняется с `persist-credentials: false`. `pull_request_target` не применяется, а для PR из форка job'ы с секретами пропускаются.

После маршрутизации (`job route`) workflow разделяет два режима: `opencode-readonly` для обычных комментариев и `opencode-write` для явных `to-spec`, `to-tickets` и `implement-spec`. Только в `opencode-write` выдаётся `permissions: issues: write` и переменная `GH_TOKEN: ${{ github.token }}`, которую получает `gh` внутри агента. В `opencode-readonly` ни того, ни другого нет.

Модель — `opencode/deepseek-v4.1-flash`: платная экономичная модель OpenCode Console с поддержкой инструментов и рассуждений. Официальный прайс на момент настройки (скидка 60%): `$0.12` за 1M входных токенов, `$0.48` за 1M выходных и `$0.0024` за 1M кэшированных чтений; без скидки — `$0.30` и `$1.20` соответственно. Ключ берётся из секретов репозитория `OPENCODE_API_KEY` или `SECRETPAY` (используется первый заданный) и не выводится в логи; ключ должен принадлежать Workspace OpenCode Console с положительным балансом, а если не задан ни один — job падает до запуска модели. Автопополнение не включается, дорогие модели не выбираются. Запуск ограничен 15 минутами, для каждого Issue/PR одновременно выполняется не более одного запуска (`concurrency` по номеру). Параметры действия: `mentions: /opencode,/oc` и `share: false`.

Навыки `to-spec`, `to-tickets` и `implement-spec` хранятся в репозитории в `.agents/skills/` и автоматически обнаруживаются OpenCode на чистом runner; отдельный шаг workflow проверяет наличие их `SKILL.md` после checkout. Дополнительная установка навыков не требуется.

Конфигурация `opencode.json` адаптирована под неинтерактивный runner: доступ к путям вне checkout и чтение `.env`/`.env.*` запрещены, вопросы пользователю тоже запрещены. Если инструмент заблокирован, агент продолжает с доступной информацией. Число шагов агента `build` увеличено с 20 до 50, чтобы `to-spec` и `to-tickets` успевали прочитать контекст и опубликовать Issue; агент `readonly` по-прежнему ограничен 20 шагами. Общий таймаут job'а остаётся 15 минут, поэтому расход запросов ограничен. Поле `$schema` добавляется вручную, чтобы загрузчик OpenCode не создавал лишний diff. Правила анализа Issue в таком режиме описаны в `docs/agents/opencode-github.md`. Workflow дополнительно проверяет наличие и связность этих файлов.

Job `opencode-readonly` передаёт `OPENCODE_CONFIG_CONTENT` из `opencode.readonly.json`: он задаёт `default_agent: readonly` и запрещает `edit`, `bash`, `task`, `webfetch`, `websearch`, `todowrite`, `skill`, `lsp`, `question` и `external_directory`. Это технический запрет, а не только инструкция модели: агент может читать репозиторий, но не может изменить файлы, выполнить shell-команду или запустить подзадачу. В результате грязного дерева не возникает, и экшн не создаёт ветку, коммит и PR. Job `opencode-write` для явных `to-spec`, `to-tickets` и `implement-spec` inline-конфиг не передаёт: остаётся агент `build` с необходимыми инструментами, `GH_TOKEN` и правом `issues: write`. Перед запуском workflow проверяет чистое дерево; после read-only запуска проверка повторяется.

Таймаут остаётся равным 15 минутам. Увеличение до 30 минут не устраняет причину зависания: оно лишь продлит ожидание и дольше будет удерживать слот `concurrency`.

### Автоматический triage новых Issues

Отдельный workflow `.github/workflows/opencode-triage.yml` срабатывает на событие `issues` типа `opened`. Он запускается только для Issues, открытых пользователем `KmKana`, и игнорирует ботов — в том числе Issues, которые создают навыки `to-spec` и `to-tickets` через GitHub Actions. Дополнительно workflow повторно читает Issue через API: метки `ready-for-agent`, `ready-for-human` и `wontfix` из `docs/agents/triage-labels.md` могут появиться уже после `opened`, поэтому triage таких подготовленных или закрытых задач пропускается. Защита от повторного triage есть и по маркерам в тексте (`Part of #N`, `## Что сделать`, `Спецификация:`).

Автоматический triage использует тот же закреплённый OpenCode Action, `OPENCODE_API_KEY` и OIDC-авторизацию GitHub App. Ему передаётся inline-конфиг `opencode.readonly.json`: агент не может менять файлы, выполнять shell-команды, вызывать подзадачи, навыки или сеть. `GH_TOKEN` не выдаётся, разрешение `issues: write` не запрашивается — результат публикуется как обычный ответ экшна в Issue. Prompt требует прочитать Issue и код приложения, сформулировать потребность, проверить уже реализованное поведение, задать уточняющие вопросы и предложить следующий шаг мейнтейнеру. Навыки `to-spec`, `to-tickets` и `implement-spec` автоматически не запускаются. Ограничения: 15 минут и `concurrency` по номеру Issue.

После объединения PR в `main` базовую проверку комментариев можно выполнить так:

1. Откройте любую Issue в этом репозитории.
2. От аккаунта `KmKana` оставьте комментарий:

   ```text
   /oc explain this issue
   ```

3. В разделе **Actions** появится запуск `opencode`; бот сначала напишет «Working...», затем заменит комментарий ответом. Ожидаемое время — не более 15 минут.

Если секрет `OPENCODE_API_KEY` не задан в **Settings → Secrets and variables → Actions**, добавьте его там перед проверкой: workflow не может работать без ключа и никогда не печатает его значение.

Для проверки автоматического triage откройте новое Issue от аккаунта `KmKana`: должен появиться отдельный запуск `opencode triage` и комментарий с разбором. Технические тикеты с меткой `ready-for-agent` (как Issue #17–#21) triage не запускают — это можно проверить по истории запусков workflow, не создавая новые Issue.

## Запуск через Docker

`Dockerfile` собирает один образ: Vite собирает статику, Maven пакует её вместе со Spring Boot, финальный слой содержит только JRE и `app.jar`. UI и `/api` отдаются с одного origin.

```bash
podman build -t callbooking .        # или docker build -t callbooking .
podman run --rm -p 8080:8080 callbooking
podman run --rm -e PORT=9090 -p 9090:9090 callbooking
```

Откройте <http://localhost:8080>. Переменная `PORT` задаёт `server.port` (по умолчанию `8080`); клиентские маршруты React пересылаются на `index.html`.

Данные хранятся только в памяти процесса: перезапуск контейнера сбрасывает типы встреч и записи. Сервис рассчитан на один инстанс.

## Публичный адрес

<https://callbooking-uklz.onrender.com> — существующий Render Web Service на бесплатном тарифе, запущенный из ветки `chore/container-deploy` исходного репозитория. Адрес проверен 23 ноября 2026 года: главная страница отдаёт приложение, `GET /api/meeting-types` возвращает JSON. Новый репозиторий к этому деплою пока не подключён. Render free plan останавливает сервис при простое, а данные при перезапуске сбрасываются — для стабильной проверки используйте локальный запуск или Docker.

## API (кратко)

| Метод | Путь | Назначение |
|-------|------|------------|
| GET | `/api/meeting-types` | Каталог типов встреч |
| POST | `/api/meeting-types` | Создать тип встречи (владелец) |
| GET | `/api/slots?meetingTypeId=…` | Свободные слоты на ближайшие 14 дней |
| POST | `/api/bookings` | Записаться на свободный слот |
| GET | `/api/bookings` | Предстоящие встречи владельца |

Полный контракт — в `api/openapi/openapi.yaml`. Рабочие правила для агентов и формат коммитов — в `AGENTS.md`.

---

<details>
<summary>Автоматические тесты Хекслета</summary>

Тесты запускаются на каждый коммит. За запуск отвечает файл `.github/workflows/hexlet-check.yml` — не удаляйте и не переименовывайте ни его, ни репозиторий.

</details>

## О Хекслете

[Хекслет](https://ru.hexlet.io/) — школа программирования: авторские программы обучения с практикой, поддержкой наставников и реальными проектами, которые остаются в резюме. Этот репозиторий — продолжение проекта первого этапа.
