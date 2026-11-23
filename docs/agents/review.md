# Независимое авторевью Pull Request

Документ описывает, как работают автоматическое ревью и бюджеты вызовов модели.
Критерии самого ревью — в `REVIEW.md`; шаблон PR — в
`.github/pull_request_template.md`. Правила OpenCode в Actions — в
`opencode-github.md`.

## Workflow

| Workflow | Событие | Что делает | Секрет модели |
|---|---|---|---|
| `.github/workflows/opencode-review.yml` | `pull_request`: `opened`, `synchronize`, `reopened`, `ready_for_review` | независимое ревью diff по `REVIEW.md`, спецификации #17, согласованию #7 и результатам CI | да, только для доверенного PR |
| `.github/workflows/pr-browser-check.yml` | те же события `pull_request` | Playwright E2E для точного head SHA, trace и HTML-отчёт как артефакт | нет |
| `.github/workflows/opencode.yml` | `issue_comment`, `pull_request_review_comment` с `/oc` или `/opencode` от `KmKana` | интерактивные explain/triage/to-spec/to-tickets/implement-spec | да, только для доверенного PR/Issue |
| `.github/workflows/opencode-triage.yml` | `issues: opened` от `KmKana` | автоматический triage нового Issue | да |

## Доверенные PR и форки

Автоматическое ревью выполняется только если одновременно:

- PR не draft;
- `github.event.pull_request.head.repo.full_name == github.repository`, то есть
  ветка PR находится в этом же репозитории, а не в форке;
- автор — `KmKana` или GitHub App OpenCode. Фактический login бота в API —
  `opencode-agent[bot]`; в `gh` он отображается как `app/opencode-agent`,
  поэтому в условии разрешены оба варианта;
- ветка не начинается с `release-please--`, а автор не `github-actions[bot]` /
  `app/github-actions`: release-please PR не тратят платный вызов модели.

Это включает PR, созданные OpenCode: они пушат ветку в этот же репозиторий.
PR из форка не получает job с секретом. Дополнительная защита — GitHub сам не
передаёт секреты на событии `pull_request` из форка; `pull_request_target` в
проекте не используется. Checkout идёт по `${{ github.event.pull_request.head.sha }}`
с `persist-credentials: false`.

Если PR меняет сам механизм ревью (workflow, `scripts/opencode-budget.mjs`,
`opencode*.json`, `REVIEW.md`, `docs/agents/review.md`), автоматическое ревью
не запускает модель: такой PR получает `review_status: manual_review_required`
и проверяется человеком. Нельзя проверять механизм его же изменённой версией.

## Роль ревьюера

`opencode-review.yml` запускает CLI OpenCode `1.19.6` с моделью
`opencode/deepseek-v4.1-flash` и inline-конфигом `opencode.review.json`.
Конфиг задаёт отдельного агента `reviewer` и технически запрещает:

- `edit`, `bash`, `task`, `webfetch`, `websearch`, `todowrite`, `question`,
  `lsp`, `skill`, `external_directory`.

Ревьюер читает только checkout и подготовленные файлы `.ai-review/`:
`REVIEW.md`, спецификацию #17, согласование #7, описание PR, diff, результаты
CI. Он не получает `GH_TOKEN`, не может выполнить команду, изменить файл,
создать коммит, ветку или PR. Публикует результат workflow, а не модель.
Публикация — `gh pr review --comment`, поэтому это отдельная роль
`github-actions[bot]`, а не автор кода.

После запуска workflow проверяет, что ревьюер не изменил tracked-файлы. Если
изменил — изменения откатываются, статус меняется на `agent_failed`.

Ревью — совещательное. Workflow не запускает исправления по комментарию
ревьюера, не вызывает `implement-spec` и не слушает события ревью-комментариев.
`opencode.yml` реагирует на `/oc` только от `KmKana`; комментарии ботов
игнорируются.

## Бюджет: максимум 3 интерактивных `/oc` на PR

Проверка выполняется job'ом `budget` в `opencode.yml` до job'ов, которые
вызывают модель. Источник счётчика — **история запусков GitHub Actions**, а не
комментарий и не файл в checkout:

- `run-name` запуска имеет вид `opencode / <PR> / <author>`;
- `scripts/opencode-budget.mjs` читает запуски workflow `opencode.yml` с
  событиями `issue_comment` и `pull_request_review_comment`;
- сам скрипт бюджета берётся из ветки по умолчанию (`main`), а не из PR,
  поэтому PR не может ослабить проверку своего бюджета;
- учитываются только запуски этого PR от `KmKana`; боты и другие Issues не
  считаются;
- каждый запуск даёт `run_attempt` попыток: повторный запуск (`Re-run`) и
  отменённая попытка тоже учитываются;
- запуск, у которого шаг модели был пропущен (например, budget уже исчерпан),
  бюджет не расходует;
- текущая попытка считается даже если API ещё не вернул её в списке.

Лимит — 3. Четвёртый `/oc` не вызывает модель. Вместо этого job
`budget-notice` публикует комментарий с `budget_status: limit_exhausted`, числом
использованных попыток, head SHA и ссылкой на логи запуска. Тот же job сообщает
`budget_status: infra_failed`, если историю запусков не удалось прочитать:
проверка работает fail-closed.

Маркер в комментарии используется только для того, чтобы не публиковать одно и
то же уведомление повторно. Удаление или редактирование комментария не
увеличивает бюджет: счётчик остаётся в истории запусков, которой агент не
управляет (у него нет `actions: write`).

## Бюджет авторевью: максимум 2 вызова на PR

`opencode-review.yml` проверяет бюджет в job'е `review` до установки и вызова
модели. Источник — история запусков workflow `opencode-review.yml`:

- `run-name` имеет вид `opencode review / <PR> / <head SHA>`;
- `scripts/opencode-budget.mjs` считает только запуски, в которых шаг
  `Run independent reviewer` действительно выполнялся (или был отменён);
- повторные и отменённые попытки учитываются;
- запуски, где модель была пропущена (лимит, тот же SHA, защита
  инфраструктуры), бюджет не расходуют;
- если для текущего head SHA уже есть успешное ревью, повторное ревью не
  запускается (`same_sha_reviewed`), даже при `reopened` или
  `ready_for_review`;
- лимит — 2 вызова модели на PR.

Исчерпание публикует `review_status: limit_exhausted` с SHA и ссылкой на логи.
Автоматические ревью на каждый push намеренно ограничены: обычный сценарий —
ревью при открытии PR и одно ревью после существенного обновления; дальше
нужен новый PR или согласованное изменение лимита.

## Статусы

| Статус | Кто публикует | Значение |
|---|---|---|
| `reviewed` | `opencode-review.yml` | модель вернула отчёт; опубликован как review |
| `limit_exhausted` | `opencode-review.yml` / `opencode.yml` | бюджет исчерпан, модель не вызывалась |
| `same_sha_reviewed` | `opencode-review.yml` | этот SHA уже ревьюился, повтор пропущен без комментария |
| `timeout` | `opencode-review.yml` | модель не уложилась в 480 секунд |
| `agent_failed` | `opencode-review.yml` | модель упала, вернула пустой ответ или изменила tracked-файлы |
| `infra_failed` | `opencode-review.yml` / `opencode.yml` | не удалось проверить бюджет, установить CLI, прочитать контекст или секрет |
| `manual_review_required` | `opencode-review.yml` | PR меняет инфраструктуру ревью; нужен человек |
| `budget_status: limit_exhausted` | `opencode.yml` | исчерпан лимит интерактивных `/oc` |
| `budget_status: infra_failed` | `opencode.yml` | не удалось проверить бюджет `/oc` |

Job'ы `opencode-review.yml` имеют `timeout-minutes: 20`; вызов модели
дополнительно ограничен `timeout` 480 секунд, ожидание CI — 360 секунд. Если
job отменён новым push, устаревшее ревью не публикуется, но отменённая попытка
остаётся в истории и учитывается бюджетом.

## Независимая браузерная проверка

`pr-browser-check.yml` — отдельный workflow, не часть `check`. Он:

- берёт `refs/pull/<n>/head` и проверяет, что `git rev-parse HEAD` равен
  `github.event.pull_request.head.sha`;
- поднимает реальные Spring Boot и React на отдельных портах, без моков API;
- запускает `frontend/playwright.pr.config.ts`: `trace: 'on'`,
  `screenshot: 'only-on-failure'`, HTML-отчёт;
- всегда загружает `frontend/playwright-report` и `frontend/test-results` как
  артефакт `playwright-pr-<sha>` (retention 7 дней), даже при падении;
- не использует секреты и безопасен для PR из форков.

Артефакт с другим SHA не является доказательством для PR. Ссылку на запуск и
имя артефакта нужно указать в PR по шаблону.

## Права

- `opencode-review.yml`: `contents: read`, `pull-requests: write`,
  `issues: read`, `checks: read`, `actions: read`. Секрет модели — только в
  шагах проверки ключа и вызова модели.
- `budget` в `opencode.yml`: `actions: read`, `contents: read`, `issues: read`,
  `pull-requests: read`; модель там не вызывается.
- `budget-notice`: `pull-requests: write`; модель не вызывается.
- `pr-browser-check.yml`: `contents: read`; секретов нет.

`pull_request_target` не используется. Экшены и CLI закреплены
(`actions/checkout@v7`, `jdx/mise-action@v5`, `actions/upload-artifact@v4`,
OpenCode `1.19.6`); автопополнение баланса не включается.

## Стоимость

Модель — `opencode/deepseek-v4.1-flash`, платная экономичная модель OpenCode
Console (прайс на момент настройки: `$0.12` / `$0.48` / `$0.0024` за 1M
входных/выходных/кэшированных токенов при скидке 60%). Жёсткие лимиты: до 3
интерактивных вызовов и до 2 авторевью на PR, шаг ревьюера — 30, таймаут
модели — 480 секунд. Это ограничивает стоимость даже при серии push. Перед
изменением лимитов сверяйтесь с актуальным прайсом и балансом Workspace.

## Как проверить без вызова модели

```bash
node --test scripts/opencode-budget.test.mjs
node --test scripts/check-gate.test.mjs
```

Тесты проверяют подсчёт попыток, повторные запуски, отменённые запуски,
исключение пропущенного шага модели, дедупликацию по SHA и границы лимитов.
Сам workflow проверяется `actionlint`; полный шлюз — `mise run check`.
