import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import { fetchMeetingTypes } from './api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    fetchMeetingTypes: vi.fn(),
  };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}</span>;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('Главная страница', () => {
  it('открывается и рассказывает о сервисе', () => {
    renderAt('/');

    expect(screen.getByRole('heading', { level: 1, name: 'Запись на звонок' })).toBeTruthy();
    expect(screen.getByText(/Упрощённый сервис бронирования времени/i)).toBeTruthy();
    expect(screen.getByTestId('location').textContent).toBe('/');
  });

  it('ведёт на страницу записи по кнопке', () => {
    renderAt('/');

    fireEvent.click(screen.getByRole('link', { name: 'Записаться на встречу' }));

    expect(screen.getByTestId('location').textContent).toBe('/booking');
    expect(screen.getByRole('heading', { level: 1, name: 'Запись скоро' })).toBeTruthy();
  });

  it('ведёт в каталог по ссылке в шапке', async () => {
    vi.mocked(fetchMeetingTypes).mockResolvedValue([
      { id: 'type-1', name: 'Знакомство', durationMinutes: 30 },
    ]);
    renderAt('/');

    fireEvent.click(screen.getByRole('link', { name: 'Каталог' }));

    expect(screen.getByTestId('location').textContent).toBe('/catalog');
    expect(screen.getByRole('heading', { level: 1, name: 'Kanybek' })).toBeTruthy();
    expect(await screen.findByRole('heading', { level: 3, name: 'Знакомство' })).toBeTruthy();
    expect(screen.queryByText('Что-то пошло не так. Попробуйте ещё раз.')).toBeNull();
  });
});
