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

Workflow `.github/workflows/opencode.yml` использует локальную копию [действия OpenCode для GitHub](https://github.com/anomalyco/opencode/blob/latest/github/README.md) из `.github/actions/opencode-pinned`, созданную по commit `77fc88c8ade8e5a620ebbe1197f3a572d29ae91a`. CLI закреплён на версии `1.19.6`: установка выполняется с `--version 1.19.6` и проверкой `opencode --version`, кэш upstream-экшна по «последнему релизу» не используется. Он срабатывает только на события `issue_comment` и `pull_request_review_comment` типа `created`, когда комментарий содержит `/oc` или `/opencode` и оставлен пользователем `KmKana`; комментарии ботов игнорируются. Используется авторизация через GitHub App (`id-token: write`, `contents: read`), checkout выполняется с `persist-credentials: false`. `pull_request_target` не применяется, а для PR из форка шаг с секретом пропускается.

Модель — `opencode/deepseek-v4.1-flash-free`: бесплатная модель из каталога OpenCode с поддержкой инструментов и рассуждений. Ключ берётся только из секрета репозитория `OPENCODE_API_KEY` и не выводится в логи. Запуск ограничен 15 минутами, для каждого Issue/PR одновременно выполняется не более одного запуска (`concurrency` по номеру). Параметры действия: `mentions: /opencode,/oc` и `share: false`.

Навыки `to-spec`, `to-tickets` и `implement-spec` хранятся в репозитории в `.agents/skills/` и автоматически обнаруживаются OpenCode на чистом runner; отдельный шаг workflow проверяет наличие их `SKILL.md` после checkout. Дополнительная установка навыков не требуется.

Конфигурация `opencode.json` адаптирована под неинтерактивный runner: доступ к путям вне checkout и чтение `.env`/`.env.*` запрещены, вопросы пользователю тоже запрещены. Если инструмент заблокирован, агент продолжает с доступной информацией; число шагов ограничено 20. Поле `$schema` добавляется вручную, чтобы загрузчик OpenCode не создавал лишний diff. Правила анализа Issue в таком режиме описаны в `docs/agents/opencode-github.md`. GitHub CLI не отключается глобально: `to-spec`, `to-tickets` и `implement-spec` по-прежнему могут работать с Issues и PR, когда это нужно задачей и разрешениями. Workflow дополнительно проверяет наличие и связность этих файлов.

Для обычных комментариев workflow передаёт `OPENCODE_CONFIG_CONTENT` из `opencode.readonly.json`: он задаёт `default_agent: readonly` и запрещает `edit`, `bash`, `task`, `webfetch`, `websearch`, `todowrite`, `skill`, `lsp`, `question` и `external_directory`. Это технический запрет, а не только инструкция модели: агент может читать репозиторий, но не может изменить файлы, выполнить shell-команду или запустить подзадачу. В результате грязного дерева не возникает, и экшн не создаёт ветку, коммит и PR. Для явных `to-spec`, `to-tickets` и `implement-spec` inline-конфиг не передаётся: остаётся агент `build` с необходимыми инструментами и доступом к GitHub Issues. Перед запуском workflow проверяет чистое дерево; после read-only запуска проверка повторяется.

Таймаут остаётся равным 15 минутам. Увеличение до 30 минут не устраняет причину зависания: оно лишь продлит ожидание и дольше будет удерживать слот `concurrency`.

После объединения PR в `main` проверку можно выполнить так:

1. Откройте любую Issue в этом репозитории.
2. От аккаунта `KmKana` оставьте комментарий:

   ```text
   /oc explain this issue
   ```

3. В разделе **Actions** появится запуск `opencode`; бот сначала напишет «Working...», затем заменит комментарий ответом. Ожидаемое время — не более 15 минут.

Если секрет `OPENCODE_API_KEY` не задан в **Settings → Secrets and variables → Actions**, добавьте его там перед проверкой: workflow не может работать без ключа и никогда не печатает его значение.

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
