# Проверка эффективности тестов (#23)

Дата: 2026-11-23. База: интеграционный тип `263024d`, ветка `ticket/23-test-effectiveness`.

Цель: доказать, что тесты защищают правило создания Записей, а не проходят формально.
Проверяемый шов — HTTP API Spring Boot и один браузерный E2E на настоящих React и Spring Boot.
Внутренние детали хранилища не являются критерием приёмки; намеренное нарушение вносится только
в `InMemoryStore.tryAddBooking`, чтобы observable-поведение API изменилось.

## Ожидаемые сигналы провала (до мутации)

Перед внесением нарушения фиксируем, какие именно проверки обязаны упасть. После мутации вывод
должен содержать все перечисленные сигналы; тесты на примыкание (стык Слотов) и остальные
календарные правила обязаны остаться зелёными.

1. `BookingConflictApiTest.sameStartReturnsSlotConflictAndListingDoesNotReserveTime` —
   повторная запись на тот же Слот Гостём ожидает `409 SLOT_CONFLICT` с сообщением
   «Выбранное время уже занято. Выберите другой слот», а получает `201 Created`.
2. `BookingConflictApiTest.partialOverlapWithSameTypeReturnsSlotConflict` —
   частичное наложение 30-минутной Записи на 60-минутную ожидает `409 SLOT_CONFLICT`,
   а получает `201 Created`.
3. `BookingConflictApiTest.differentTypesCannotOverlap` — пересечение разных Типов встреч
   ожидает `409 SLOT_CONFLICT`, а получает `201 Created`.
4. `BookingConflictApiTest.conflictIsDetectedAgainAfterSlotWasListed` — повторная проверка
   доступности в `POST /api/bookings` ожидает `409 SLOT_CONFLICT`, а получает `201 Created`.
5. `ErrorCodesContractApiTest.slotConflictHasConflictStatusAndFixedMessage` — код ошибки
   `SLOT_CONFLICT` ожидает `409` и фиксированное сообщение, а получает `201 Created`.
6. `BookingConcurrencyTest.onlyOneOfTwelveConcurrentHttpRequestsWinsTheSlot` и
   `BookingConcurrencyTest.onlyOneOfThirtyTwoConcurrentHttpRequestsWinsTheSlot` — ожидают ровно
   одного победителя (`created == 1`, `conflicts == requests - 1`) и ровно одну Запись
   у Владельца; при мутации все параллельные запросы получают `201` и Записей становится 12/32.
7. E2E `frontend/e2e/booking.spec.ts` («гость записывается на слот, конфликт из устаревшей вкладки
   не создаёт вторую запись») — устаревшая вкладка ожидает текст конфликта, обновлённые Слоты
   и ровно одну Запись; при мутации конфликтного сообщения не будет, а Записей станет две.

Дополнительные инварианты проверки:

- синхронизация (`synchronized` и один монитор `InMemoryStore`) не ослабляется;
- никакое другое правило (окно 14 дней, часовой пояс `Asia/Almaty`, сетка 30 минут,
  рабочие часы, длительность Типа встречи) не меняется;
- сгенерированные OpenAPI и SDK, а также `.github/workflows/hexlet-check.yml` не трогаются;
- временное нарушение полностью устраняется, `git status --porcelain` в финале пуст.

## 1. Зелёный baseline до мутации

Команда (из корня ворктри):

```powershell
$MISE = 'C:\Users\user\AppData\Local\Microsoft\WinGet\Links\mise.exe'
& $MISE run check
```

Результат: `MISE_EXIT=0`, все задачи шлюза завершились успешно.

| Проверка | Результат |
|---|---|
| Spotless (`lint-backend`) | `BUILD SUCCESS`, 45 файлов уже отформатированы |
| ESLint + Prettier (`lint-frontend`) | успешно |
| Контракт и артефакты (`check-contract`) | 5 операций и 6 кодов ошибок согласованы, `git` чист |
| Единый шлюз (`test-check-gate`) | `tests 4`, `pass 4`, `fail 0` |
| JUnit (`test`) | `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0` |
| Vitest (`test-frontend`) | `Test Files 8 passed (8)`, `Tests 19 passed (19)` |
| Сборка React (`build-frontend`) | успешно |
| Браузерный E2E (`test-e2e`) | `1 passed (22.7s)` — сценарий гостя с устаревшими Слотами |

## 2. Намеренное нарушение правила записи

Изменён только один фрагмент `backend/src/main/java/io/hexlet/callbooking/store/InMemoryStore.java`
в методе `tryAddBooking` (проверка пересечения Записей заменена на `null`):

```diff
-    Booking conflict = BookingOverlap.conflict(bookings, start, end).orElse(null);
+    // МУТАЦИЯ #23: намеренно отключаем проверку пересечения, синхронизация не трогается.
+    Booking conflict = null;
     if (conflict != null) {
```

`BookingOverlap.conflict` по-прежнему хранит правило `existing.start < new.end && new.start < existing.end`.
`tryAddBooking` остаётся `synchronized` на том же мониторе; окно 14 дней, часовой пояс, сетка,
рабочие часы и длительность Типа встречи не менялись. Ожидаемый observable-эффект: сервер принимает
пересекающиеся Записи и отвечает `201 Created` вместо `409 SLOT_CONFLICT`.

Команда красного прогона (из `backend/`, аргумент `-Dtest` взят в кавычки для PowerShell):

```powershell
$MISE = 'C:\Users\user\AppData\Local\Microsoft\WinGet\Links\mise.exe'
& $MISE exec -- mvn -B test '-Dtest=BookingConflictApiTest,ErrorCodesContractApiTest,BookingConcurrencyTest'
```

Результат: `MVN_EXIT=1`, `Tests run: 13, Failures: 7, Errors: 0, Skipped: 0`, `BUILD FAILURE`.
Все семь ожидаемых JUnit-сигналов сработали:

| Тест | Фактическое сообщение |
|---|---|
| `BookingConflictApiTest.sameStartReturnsSlotConflictAndListingDoesNotReserveTime` | `Status expected:<409> but was:<201>` |
| `BookingConflictApiTest.partialOverlapWithSameTypeReturnsSlotConflict` | `Status expected:<409> but was:<201>` |
| `BookingConflictApiTest.differentTypesCannotOverlap` | `Status expected:<409> but was:<201>` |
| `BookingConflictApiTest.conflictIsDetectedAgainAfterSlotWasListed` | `Status expected:<409> but was:<201>` |
| `ErrorCodesContractApiTest.slotConflictHasConflictStatusAndFixedMessage` | `Status expected:<409> but was:<201>` |
| `BookingConcurrencyTest.onlyOneOfTwelveConcurrentHttpRequestsWinsTheSlot` | `expected: 1 but was: 12` (победителей и Записей стало 12) |
| `BookingConcurrencyTest.onlyOneOfThirtyTwoConcurrentHttpRequestsWinsTheSlot` | `expected: 1 but was: 32` (победителей и Записей стало 32) |

Тесты на примыкание Слотов и остальные календарные проверки в этом прогоне не провалились.

Дополнительно при той же мутации запущен браузерный E2E:

```powershell
& $MISE run test-e2e
```

Результат: `E2E_EXIT=1`, `1 failed`. Сценарий «гость записывается на слот, конфликт из устаревшей
вкладки не создаёт вторую запись» упал в `frontend/e2e/booking.spec.ts:94`:

```text
Error: expect(locator).toHaveText(expected) failed
Locator: getByTestId('booking-conflict')
Expected: "Выбранное время уже занято. Выберите другой слот"
Timeout: 15000ms
Error: element(s) not found
```

То есть устаревшая вкладка получила `201` и создала вторую Запись вместо ожидаемого `409` —
тот же дефект, который поймали API-тесты, подтверждён через реальный UI Владельца и Гостя.

## 3. Восстановление и повторный зелёный прогон

```powershell
git checkout -- backend/src/main/java/io/hexlet/callbooking/store/InMemoryStore.java
git status --porcelain
```

После восстановления `git status --porcelain` показал только `?? docs/test-effectiveness.md`;
`git diff -- backend/src/main/java/io/hexlet/callbooking/store/InMemoryStore.java` — пустой.
Временного нарушения в репозитории не осталось.

Повторный `& $MISE run check` после восстановления и после объединения с актуальным концом
`integration/spec-12-booking` (`263024d`, `Already up to date`) дал `MISE_EXIT=0`:

| Проверка | Результат |
|---|---|
| Контракт и артефакты (`check-contract`) | `OK: 5 операций и 6 кодов ошибок`, сгенерированные артефакты согласованы с git |
| ESLint + Prettier (`lint-frontend`) | `All matched files use Prettier code style!` |
| Spotless (`lint-backend`) | `BUILD SUCCESS` |
| Единый шлюз (`test-check-gate`) | `tests 4`, `pass 4`, `fail 0` |
| JUnit (`test`) | `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0` |
| Vitest (`test-frontend`) | `Test Files 8 passed (8)`, `Tests 19 passed (19)` |
| Сборка React (`build-frontend`) | успешно |
| Браузерный E2E (`test-e2e`) | `1 passed (17.9s)` |

Вывод: тесты действительно защищают правило «нельзя создавать пересекающиеся Записи»:
устранение проверки пересечения ломает 7 API/конкурентных JUnit-тестов и браузерный сценарий,
а после восстановления весь единый шлюз снова зелёный.

Дата проверки: 2026-11-23. Коммит отчёта: `docs: record test effectiveness check (#23)`
(текущий HEAD ветки `ticket/23-test-effectiveness`).
