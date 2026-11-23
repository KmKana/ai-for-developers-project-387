import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiCreateBooking, apiListAvailableSlots, apiListMeetingTypes } from '../sdk';
import type { ApiError, Booking, MeetingType, Slot } from '../sdk';
import { failure, response } from '../test/sdkMocks';
import BookingPage from './BookingPage';

vi.mock('../sdk', async (importOriginal) => {
  const { mockSdkModule } = await import('../test/sdkMocks');
  return mockSdkModule(importOriginal, [
    'apiListMeetingTypes',
    'apiListAvailableSlots',
    'apiCreateBooking',
  ]);
});

const types: MeetingType[] = [
  {
    id: 'type-intro',
    name: 'Знакомство',
    description: 'Короткий созвон о вашем проекте',
    durationMinutes: 30,
  },
];

const slots: Slot[] = [{ start: '2026-03-11T05:00:00Z', end: '2026-03-11T05:30:00Z' }];

const booking: Booking = {
  id: 'booking-1',
  meetingTypeId: 'type-intro',
  meetingTypeName: 'Знакомство',
  start: '2026-03-11T05:00:00Z',
  end: '2026-03-11T05:30:00Z',
  guestName: 'Иван Петров',
  guestEmail: 'ivan@example.com',
  createdAt: '2026-03-10T11:00:00+05:00',
};

function renderBookingPage() {
  return render(
    <MemoryRouter initialEntries={['/book/type-intro']}>
      <Routes>
        <Route path="/book/:id" element={<BookingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function chooseSlotAndFillForm() {
  fireEvent.click(await screen.findByRole('button', { name: '10:00 · 30 мин' }, { timeout: 5000 }));
  fireEvent.change(screen.getByPlaceholderText('Как вас зовут?'), {
    target: { value: 'Иван Петров' },
  });
  fireEvent.change(screen.getByPlaceholderText('you@example.com'), {
    target: { value: 'ivan@example.com' },
  });
  fireEvent.click(screen.getByRole('button', { name: /Подтвердить запись/ }));
}

function mockCatalog() {
  vi.mocked(apiListMeetingTypes).mockResolvedValue(
    response(types, 'http://localhost/api/meeting-types'),
  );
  vi.mocked(apiListAvailableSlots).mockResolvedValue(response(slots, 'http://localhost/api/slots'));
}

beforeEach(() => {
  // 10 марта 2026 года: окно записи — 11–24 марта, включая выходные 14–15 и 21–22.
  vi.useFakeTimers({ toFake: ['Date'], shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-03-10T06:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('Подтверждение Записи на Странице записи', () => {
  it('показывает ответ SDK, длительность Слота и ведёт в каталог за новой записью', async () => {
    mockCatalog();
    vi.mocked(apiCreateBooking).mockResolvedValue(
      response(booking, 'http://localhost/api/bookings', 201),
    );

    renderBookingPage();
    await chooseSlotAndFillForm();

    expect(
      await screen.findByRole('heading', { name: 'Встреча забронирована!' }, { timeout: 5000 }),
    ).toBeTruthy();
    expect(screen.getByText('Знакомство')).toBeTruthy();
    expect(screen.getByText(/11 марта в 10:00 – 10:30 \(Asia\/Almaty\)/)).toBeTruthy();
    expect(screen.getByText(/Иван Петров · ivan@example.com/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Записаться ещё раз' }).getAttribute('href')).toBe(
      '/catalog',
    );
    expect(apiCreateBooking).toHaveBeenCalledWith({
      body: {
        meetingTypeId: 'type-intro',
        start: '2026-03-11T05:00:00Z',
        guestName: 'Иван Петров',
        guestEmail: 'ivan@example.com',
      },
    });
  });

  it('показывает сообщение сервера при ошибке валидации', async () => {
    mockCatalog();
    vi.mocked(apiCreateBooking).mockResolvedValue(
      failure(
        { code: 'VALIDATION_ERROR', message: 'Некорректный email гостя' } satisfies ApiError,
        'http://localhost/api/bookings',
        400,
      ),
    );

    renderBookingPage();
    await chooseSlotAndFillForm();

    expect(await screen.findByText('Некорректный email гостя', {}, { timeout: 5000 })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Встреча забронирована!' })).toBeNull();
  });
});
