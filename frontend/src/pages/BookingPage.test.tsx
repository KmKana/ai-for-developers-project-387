import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiListAvailableSlots, apiListMeetingTypes } from '../sdk';
import type { MeetingType, Slot } from '../sdk';
import { response } from '../test/sdkMocks';
import BookingPage from './BookingPage';

vi.mock('../sdk', async (importOriginal) => {
  const { mockSdkModule } = await import('../test/sdkMocks');
  return mockSdkModule(importOriginal, ['apiListMeetingTypes', 'apiListAvailableSlots']);
});

const types: MeetingType[] = [
  {
    id: 'type-intro',
    name: 'Знакомство',
    description: 'Короткий созвон о вашем проекте',
    durationMinutes: 30,
  },
];

const slots: Slot[] = [
  { start: '2026-03-11T09:00:00+05:00', end: '2026-03-11T09:30:00+05:00' },
  { start: '2026-03-11T10:30:00+05:00', end: '2026-03-11T11:00:00+05:00' },
];

beforeEach(() => {
  // Окно записи считается по времени владельца: 10 марта 2026 года → 11–24 марта.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-03-10T06:00:00Z'));
});

beforeEach(() => {
  // 10 марта 2026 года: окно записи — 11–24 марта, включая выходные 14–15 и 21–22.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-03-10T06:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('Страница записи', () => {
  it('показывает все 14 дней окна, недоступные выходные и Слоты из SDK', async () => {
    vi.mocked(apiListMeetingTypes).mockResolvedValue(
      response(types, 'http://localhost/api/meeting-types'),
    );
    vi.mocked(apiListAvailableSlots).mockResolvedValue(
      response(slots, 'http://localhost/api/slots'),
    );

    render(
      <MemoryRouter initialEntries={['/book/type-intro']}>
        <Routes>
          <Route path="/book/:id" element={<BookingPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Знакомство' })).toBeTruthy();
    expect(screen.getByRole('button', { name: '09:00 · 30 мин' })).toBeTruthy();
    expect(screen.getByRole('button', { name: '10:30 · 30 мин' })).toBeTruthy();
    expect(apiListAvailableSlots).toHaveBeenCalledWith({
      query: { meetingTypeId: 'type-intro' },
    });

    const chips = screen.getAllByTestId('day-chip');
    expect(chips.map((chip) => chip.getAttribute('data-day'))).toEqual([
      '2026-03-11',
      '2026-03-12',
      '2026-03-13',
      '2026-03-14',
      '2026-03-15',
      '2026-03-16',
      '2026-03-17',
      '2026-03-18',
      '2026-03-19',
      '2026-03-20',
      '2026-03-21',
      '2026-03-22',
      '2026-03-23',
      '2026-03-24',
    ]);
    expect(
      chips
        .filter((chip) => chip.hasAttribute('disabled'))
        .map((chip) => chip.getAttribute('data-day')),
    ).toEqual(['2026-03-14', '2026-03-15', '2026-03-21', '2026-03-22']);
    // День без Слотов остаётся видимым, но не предлагает время.
    const emptyDay = chips.find((chip) => chip.getAttribute('data-day') === '2026-03-12');
    expect(emptyDay?.hasAttribute('disabled')).toBe(false);
    fireEvent.click(emptyDay!);
    expect(
      screen.getByText('В этот день свободных слотов нет — выберите другой день.'),
    ).toBeTruthy();
    expect(screen.queryAllByTestId('slot-button')).toHaveLength(0);
  });

  it('показывает все 14 дней окна, включая выходные без Слотов', async () => {
    vi.mocked(apiListMeetingTypes).mockResolvedValue(
      response(types, 'http://localhost/api/meeting-types'),
    );
    vi.mocked(apiListAvailableSlots).mockResolvedValue(
      response(slots, 'http://localhost/api/slots'),
    );

    render(
      <MemoryRouter initialEntries={['/book/type-intro']}>
        <Routes>
          <Route path="/book/:id" element={<BookingPage />} />
        </Routes>
      </MemoryRouter>,
    );

    const days = await screen.findAllByTestId('day-chip');
    expect(days).toHaveLength(14);
    expect(days[0].getAttribute('data-day')).toBe('2026-03-11');
    expect(days[13].getAttribute('data-day')).toBe('2026-03-24');

    // Выходные видны в окне записи, но недоступны для выбора: у них нет Слотов.
    const saturday = days.find((day) => day.getAttribute('data-day') === '2026-03-14');
    expect(saturday?.className).toContain('weekend');
    expect(saturday?.hasAttribute('disabled')).toBe(true);
    fireEvent.click(saturday!);
    expect(screen.queryAllByTestId('slot-button')).not.toHaveLength(0);
  });
});
