import { randomUUID } from 'node:crypto';

import { expect, test } from '@playwright/test';

import { apiCreateMeetingType, apiListBookings, type Booking, type MeetingType } from '../src/sdk';
import { client } from '../src/sdk/client.gen';
import { backendUrl } from './config';

const CONFLICT_MESSAGE = 'Выбранное время уже занято. Выберите другой слот';

// E2E обращается к отдельному бэкенду через сгенерированный SDK. Адаптер
// frontend/src/api.ts не импортируется: он настраивает клиент на origin браузера.
// Тип Booking из SDK используется для проверки ответа без ручного описания полей.
client.setConfig({ baseUrl: backendUrl });

async function createMeetingType(name: string): Promise<MeetingType> {
  const { data, error, response } = await apiCreateMeetingType({
    body: { name, description: 'E2E: полный путь Гостя', durationMinutes: 30 },
  });
  expect(response.status, `POST /api/meeting-types: ${JSON.stringify(error)}`).toBe(201);
  expect(error).toBeUndefined();
  if (!data) {
    throw new Error('Сервер не вернул созданный Тип встречи');
  }
  return data;
}

test('гость записывается на слот, конфликт из устаревшей вкладки не создаёт вторую запись', async ({
  page,
  context,
}) => {
  const suffix = randomUUID();
  const typeName = `E2E встреча ${suffix}`;
  const guestName = `Гость ${suffix}`;
  const guestEmail = `guest-${suffix}@example.com`;
  const staleGuestName = `Гость со старой страницы ${suffix}`;
  const staleGuestEmail = `stale-${suffix}@example.com`;

  const meetingType = await createMeetingType(typeName);

  // Вторая вкладка заранее загружает Слоты и остаётся с устаревшим состоянием.
  const stalePage = await context.newPage();
  await stalePage.goto('/catalog');
  await stalePage
    .getByTestId('meeting-type-card')
    .filter({ hasText: typeName })
    .getByTestId('book-link')
    .click();
  const days = stalePage.getByTestId('day-chip');
  await expect(days.first()).toBeVisible();
  expect(await days.count()).toBe(14);
  const staleDay = days.first();
  const dayKey = await staleDay.getAttribute('data-day');
  expect(dayKey).not.toBeNull();
  await staleDay.click();
  const staleSlot = stalePage.getByTestId('slot-button').first();
  await expect(staleSlot).toBeVisible();
  const staleStart = await staleSlot.getAttribute('data-start');
  expect(staleStart).not.toBeNull();
  await staleSlot.click();
  await stalePage.getByTestId('guest-name').fill(staleGuestName);
  await stalePage.getByTestId('guest-email').fill(staleGuestEmail);

  // Каталог → Тип встречи → день → свободный Слот → имя и email → подтверждение.
  await page.goto('/catalog');
  const card = page.getByTestId('meeting-type-card').filter({ hasText: typeName });
  await expect(card).toBeVisible();
  await card.getByTestId('book-link').click();
  await page.locator(`[data-testid="day-chip"][data-day="${dayKey}"]`).click();
  await page.locator(`[data-testid="slot-button"][data-start="${staleStart}"]`).click();
  await expect(page.getByTestId('submit-booking')).toBeEnabled();
  await page.getByTestId('guest-name').fill(guestName);
  await page.getByTestId('guest-email').fill(guestEmail);
  await page.getByTestId('submit-booking').click();

  const confirmation = page.getByTestId('booking-success');
  await expect(confirmation).toContainText('Встреча забронирована!');
  await expect(confirmation).toContainText(typeName);
  await expect(confirmation).toContainText(guestName);
  await expect(confirmation).toContainText(guestEmail);

  // Владелец видит Запись сразу после подтверждения Гостя.
  await page.goto('/bookings');
  const ownerRow = page.getByTestId('booking-row').filter({ hasText: guestName });
  await expect(ownerRow).toBeVisible();
  await expect(ownerRow).toContainText(typeName);
  await expect(ownerRow).toContainText(guestEmail);

  // Устаревшая вкладка отправляет тот же Слот после успешной Записи.
  await stalePage.getByTestId('submit-booking').click();
  await expect(stalePage.getByTestId('booking-conflict')).toHaveText(CONFLICT_MESSAGE);
  await expect(
    stalePage.locator(`[data-testid="slot-button"][data-start="${staleStart}"]`),
  ).toHaveCount(0);

  const {
    data: bookings,
    error: bookingsError,
    response: bookingsResponse,
  } = await apiListBookings();
  expect(bookingsResponse.status).toBe(200);
  expect(bookingsError).toBeUndefined();
  const savedBookings: Booking[] = bookings ?? [];
  expect(savedBookings).toHaveLength(1);
  expect(savedBookings[0].meetingTypeId).toBe(meetingType.id);
  expect(savedBookings[0].guestName).toBe(guestName);
  expect(savedBookings[0].start).toBe(staleStart);

  // После конфликта доступен другой актуальный Слот, вторая Запись не создана.
  const freshSlots = stalePage.getByTestId('slot-button');
  await expect(freshSlots.first()).toBeVisible();
  expect(await freshSlots.first().getAttribute('data-start')).not.toBe(staleStart);
  await freshSlots.first().click();
  await expect(stalePage.getByTestId('submit-booking')).toBeEnabled();
});
