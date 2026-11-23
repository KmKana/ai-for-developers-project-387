# Демонстрация `limit_exhausted` без вызова модели

Этот файл — доказательство для задания №4. Он показывает, как четвёртый
интерактивный `/oc` на одном PR получает `limit_exhausted`, не расходуя
платные вызовы модели.

## Как устроен счётчик

- Источник счётчика — история запусков GitHub Actions, а не комментарий.
- Имя запуска: `opencode / <PR> / <author>`.
- Считаются запуски этого PR от `KmKana`; боты и другие Issues не считаются.
- `run_attempt` учитывает `Re-run`; отменённые попытки учитываются.
- Запуск, где шаг модели был пропущен, бюджет не расходует.
- Лимит: `INTERACTIVE_MAX = 3` в `scripts/opencode-budget.mjs` и
  `MAX_CALLS: "3"` в `.github/workflows/opencode.yml`.
- Четвёртый вызов: `decideInteractive` возвращает
  `allowed=false, reason=limit_exhausted`; job'ы модели запускаются только при
  `needs.budget.outputs.allowed == 'true'`, а `budget-notice` публикует
  `budget_status: limit_exhausted` с head SHA и ссылкой на логи.

## Безопасная демонстрация (выполнена)

Вместо трёх платных `/oc` вызовов использована та же функция решения, которую
вызывает workflow. Ни один вызов модели не выполнялся.

```text
$ node <demo-скрипт с decideInteractive>
used=1 allowed=true reason=ok
used=2 allowed=true reason=ok
used=3 allowed=true reason=ok
used=4 allowed=false reason=limit_exhausted
```

```text
$ node --test --test-name-pattern "blocks the fourth call" scripts/opencode-budget.test.mjs
✔ interactive budget blocks the fourth call and counts reruns
ℹ tests 1
ℹ pass 1
ℹ fail 0
```

Полный вывод: `docs/demo/limit-exhausted-output.txt`.

## Почему это корректное доказательство

`decideInteractive` — единственная функция, которая принимает решение
«разрешить/заблокировать» в `scripts/opencode-budget.mjs`. Workflow вызывает
её через `node scripts/opencode-budget.mjs` до установки и запуска модели.
Тест `interactive budget blocks the fourth call and counts reruns` проверяет
именно четвёртую попытку и повторные запуски.

## Если нужна живая демонстрация

Живой сценарий потребует трёх реальных `/oc` на тестовом PR, после чего
четвёртый будет заблокирован. Это стоит до трёх платных вызовов модели, поэтому
в рамках этого PR он намеренно не запускался. Такой PR должен быть черновиком
(draft), чтобы независимое авторевью не расходовало ещё один вызов модели.
