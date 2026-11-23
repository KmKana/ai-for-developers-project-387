import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { apiCreateMeetingType, apiListMeetingTypes } from '../sdk';
import type { ApiError, MeetingType } from '../sdk';
import EventTypesPage from './EventTypesPage';

vi.mock('../sdk', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../sdk')>();
  return {
    ...actual,
    apiListMeetingTypes: vi.fn(),
    apiCreateMeetingType: vi.fn(),
  };
});

type ListResult = Awaited<ReturnType<typeof apiListMeetingTypes>>;
type CreateResult = Awaited<ReturnType<typeof apiCreateMeetingType>>;

const request = new Request('http://localhost/api/meeting-types');

function listResponse(types: MeetingType[]): ListResult {
  return {
    data: types,
    error: undefined,
    request,
    response: new Response(JSON.stringify(types), { status: 200 }),
  } as ListResult;
}

function createResponse(body: MeetingType | ApiError, status: number): CreateResult {
  return {
    data: status < 400 ? body : undefined,
    error: status >= 400 ? body : undefined,
    request,
    response: new Response(JSON.stringify(body), { status }),
  } as CreateResult;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Форма владельца: типы встреч', () => {
  it('показывает сообщение сервера при ошибке создания', async () => {
    const failure: ApiError = {
      code: 'VALIDATION_ERROR',
      message: 'Название не длиннее 100 символов',
    };
    vi.mocked(apiListMeetingTypes).mockResolvedValue(listResponse([]));
    vi.mocked(apiCreateMeetingType).mockResolvedValue(createResponse(failure, 400));
    render(<EventTypesPage />);

    fireEvent.change(screen.getByPlaceholderText('Знакомство'), {
      target: { value: 'Н'.repeat(101) },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Создать тип встречи' }));

    expect(await screen.findByText('Название не длиннее 100 символов')).toBeTruthy();
  });

  it('добавляет созданный тип в список без перезагрузки', async () => {
    const created: MeetingType = {
      id: 'type-1',
      name: 'Знакомство',
      description: 'Короткий созвон',
      durationMinutes: 30,
    };
    vi.mocked(apiListMeetingTypes).mockResolvedValue(listResponse([]));
    vi.mocked(apiCreateMeetingType).mockResolvedValue(createResponse(created, 201));
    render(<EventTypesPage />);

    fireEvent.change(screen.getByPlaceholderText('Знакомство'), {
      target: { value: 'Знакомство' },
    });
    fireEvent.change(
      screen.getByPlaceholderText('Короткий созвон: знакомимся и обсуждаем задачу'),
      { target: { value: 'Короткий созвон' } },
    );
    fireEvent.click(screen.getByRole('button', { name: 'Создать тип встречи' }));

    expect(await screen.findByText('Знакомство')).toBeTruthy();
    expect(screen.getByText('Короткий созвон')).toBeTruthy();
  });
});
