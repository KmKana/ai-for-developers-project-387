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

const TOPIC_TOO_LONG = 'Тема разговора не длиннее 300 символов';
const emoji = (count: number) => '😀'.repeat(count);

function renderBookingPage() {
  return render(
    <MemoryRouter initialEntries={['/book/type-intro']}>
      <Routes>
        <Route path="/book/:id" element={<BookingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function chooseSlotAndFillForm(topic?: string) {
  fireEvent.click(await screen.findByRole('button', { name: '10:00 · 30 мин' }, { timeout: 5000 }));
  fireEvent.change(screen.getByPlaceholderText('Как вас зовут?'), {
    target: { value: 'Иван Петров' },
  });
  fireEvent.change(screen.getByPlaceholderText('you@example.com'), {
    target: { value: 'ivan@example.com' },
  });
  if (topic !== undefined) {
    fireEvent.change(screen.getByTestId('guest-topic'), { target: { value: topic } });
  }
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

  it('передаёт тему в создание Записи и показывает её на подтверждении', async () => {
    mockCatalog();
    const bookedWithTopic: Booking = { ...booking, topic: 'Обсудить проект' };
    vi.mocked(apiCreateBooking).mockResolvedValue(
      response(bookedWithTopic, 'http://localhost/api/bookings', 201),
    );

    renderBookingPage();
    await chooseSlotAndFillForm('  Обсудить проект  ');

    expect(
      await screen.findByRole('heading', { name: 'Встреча забронирована!' }, { timeout: 5000 }),
    ).toBeTruthy();
    expect(screen.getByText('Тема разговора')).toBeTruthy();
    expect(screen.getByText('Обсудить проект')).toBeTruthy();
    expect(apiCreateBooking).toHaveBeenCalledWith({
      body: {
        meetingTypeId: 'type-intro',
        start: '2026-03-11T05:00:00Z',
        guestName: 'Иван Петров',
        guestEmail: 'ivan@example.com',
        topic: 'Обсудить проект',
      },
    });
  });

  it('передаёт 160 emoji целиком: длина считается в кодовых точках Unicode', async () => {
    mockCatalog();
    const topic = emoji(160);
    expect(topic.length).toBe(320);
    expect(Array.from(topic)).toHaveLength(160);
    vi.mocked(apiCreateBooking).mockResolvedValue(
      response({ ...booking, topic }, 'http://localhost/api/bookings', 201),
    );

    renderBookingPage();
    await chooseSlotAndFillForm(topic);

    expect(
      await screen.findByRole('heading', { name: 'Встреча забронирована!' }, { timeout: 5000 }),
    ).toBeTruthy();
    expect(apiCreateBooking).toHaveBeenCalledWith({
      body: {
        meetingTypeId: 'type-intro',
        start: '2026-03-11T05:00:00Z',
        guestName: 'Иван Петров',
        guestEmail: 'ivan@example.com',
        topic,
      },
    });
    expect(screen.queryByTestId('topic-length-error')).toBeNull();
  });

  it('принимает ровно 300 emoji', async () => {
    mockCatalog();
    const topic = emoji(300);
    expect(Array.from(topic)).toHaveLength(300);
    vi.mocked(apiCreateBooking).mockResolvedValue(
      response({ ...booking, topic }, 'http://localhost/api/bookings', 201),
    );

    renderBookingPage();
    await chooseSlotAndFillForm(topic);

    expect(
      await screen.findByRole('heading', { name: 'Встреча забронирована!' }, { timeout: 5000 }),
    ).toBeTruthy();
    expect(apiCreateBooking).toHaveBeenCalledWith({
      body: {
        meetingTypeId: 'type-intro',
        start: '2026-03-11T05:00:00Z',
        guestName: 'Иван Петров',
        guestEmail: 'ivan@example.com',
        topic,
      },
    });
    expect(screen.queryByTestId('topic-length-error')).toBeNull();
  });

  it('показывает ошибку для 301 emoji и не отправляет запрос', async () => {
    mockCatalog();
    const topic = emoji(301);
    expect(Array.from(topic)).toHaveLength(301);

    renderBookingPage();
    await chooseSlotAndFillForm(topic);

    const error = await screen.findByTestId('topic-length-error');
    expect(error.textContent).toBe(TOPIC_TOO_LONG);
    expect(apiCreateBooking).not.toHaveBeenCalled();
    expect((screen.getByTestId('guest-topic') as HTMLTextAreaElement).value).toBe(topic);
    expect(screen.queryByRole('heading', { name: 'Встреча забронирована!' })).toBeNull();
  });

  it('не обрезает вставленный текст длиннее лимита и не отправляет его', async () => {
    mockCatalog();
    const pasted = 'я'.repeat(301);

    renderBookingPage();
    await chooseSlotAndFillForm(pasted);

    const error = await screen.findByTestId('topic-length-error');
    expect(error.textContent).toBe(TOPIC_TOO_LONG);
    expect((screen.getByTestId('guest-topic') as HTMLTextAreaElement).value).toBe(pasted);
    expect(apiCreateBooking).not.toHaveBeenCalled();
    expect(screen.queryByRole('heading', { name: 'Встреча забронирована!' })).toBeNull();
  });

  it('не показывает блок темы, если тема не указана', async () => {
    mockCatalog();
    vi.mocked(apiCreateBooking).mockResolvedValue(
      response(booking, 'http://localhost/api/bookings', 201),
    );

    renderBookingPage();
    await chooseSlotAndFillForm();

    expect(
      await screen.findByRole('heading', { name: 'Встреча забронирована!' }, { timeout: 5000 }),
    ).toBeTruthy();
    expect(screen.queryByText('Тема разговора')).toBeNull();
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
