import { cleanup, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiCallError, fetchBookings, fetchMeetingTypes } from '../api';
import BookingsPage from './BookingsPage';
import CatalogPage from './CatalogPage';
import EventTypesPage from './EventTypesPage';

const API_ERROR = 'Что-то пошло не так. Попробуйте ещё раз.';

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    fetchBookings: vi.fn(),
    fetchMeetingTypes: vi.fn(),
  };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage(page: ReactNode) {
  return render(<MemoryRouter>{page}</MemoryRouter>);
}

describe('Состояния загрузки', () => {
  it('каталог при ошибке не показывает пустой список', async () => {
    vi.mocked(fetchMeetingTypes).mockRejectedValue(new ApiCallError());

    renderPage(<CatalogPage />);

    expect(await screen.findByText(API_ERROR)).toBeTruthy();
    expect(screen.queryByText(/Владелец ещё не создал типы встреч/)).toBeNull();
  });

  it('типы встреч при ошибке не показывают пустой список', async () => {
    vi.mocked(fetchMeetingTypes).mockRejectedValue(new ApiCallError());

    renderPage(<EventTypesPage />);

    expect(await screen.findByText(API_ERROR)).toBeTruthy();
    expect(screen.queryByText('Типов встреч пока нет.')).toBeNull();
  });

  it('предстоящие встречи при ошибке не показывают пустой список', async () => {
    vi.mocked(fetchBookings).mockRejectedValue(new ApiCallError());

    renderPage(<BookingsPage />);

    expect(await screen.findByText(API_ERROR)).toBeTruthy();
    expect(screen.queryByText(/Пока нет записей/)).toBeNull();
  });

  it('пустой список без ошибки по-прежнему показывает сообщение о пустоте', async () => {
    vi.mocked(fetchBookings).mockResolvedValue([]);

    renderPage(<BookingsPage />);

    expect(await screen.findByText(/Пока нет записей/)).toBeTruthy();
    expect(screen.queryByText(/Что-то пошло не так/)).toBeNull();
  });
});
