import {
  apiCreateBooking,
  apiCreateMeetingType,
  apiListAvailableSlots,
  apiListBookings,
  apiListMeetingTypes,
} from './sdk';
import { client } from './sdk/client.gen';

import type {
  ApiError,
  ApiErrorCode,
  Booking,
  BookingInput,
  MeetingType,
  MeetingTypeInput,
  Slot,
} from './sdk/types.gen';

// Фронтенд общается с API по тому же origin: в dev это прокси Vite,
// в проде — тот же сервер. Абсолютный адрес из контракта не нужен.
client.setConfig({ baseUrl: '' });

type ErrorCodeKind = 'validation' | 'conflict' | 'server';

/**
 * ApiErrorCode в SDK — это TypeScript union: во время выполнения он исчезает.
 * Поэтому сценарии кодов перечислены один раз здесь. `satisfies` заставляет
 * карту покрывать весь union, а KNOWN_CODES и CONFLICT_CODES выводятся из неё.
 */
const ERROR_CODE_KINDS = {
  VALIDATION_ERROR: 'validation',
  MEETING_TYPE_NOT_FOUND: 'validation',
  SLOT_CONFLICT: 'conflict',
  OFF_GRID: 'conflict',
  OUTSIDE_WORKING_HOURS: 'conflict',
  OUT_OF_WINDOW: 'conflict',
  INTERNAL_ERROR: 'server',
} as const satisfies Record<ApiErrorCode, ErrorCodeKind>;

/** Все коды контракта: только их сообщения можно показывать Гостю. */
const KNOWN_CODES = Object.keys(
  ERROR_CODE_KINDS,
) as ApiErrorCode[] satisfies readonly ApiErrorCode[];

/** Коды 409-ответов: время недоступно, нужно обновить Слоты. */
const CONFLICT_CODES = new Set(KNOWN_CODES.filter((code) => ERROR_CODE_KINDS[code] === 'conflict'));

/** Форма ошибки берётся из сгенерированного SDK, а не описывается вручную. */
type ApiFailure = Partial<ApiError>;

/** Ошибка вызова API с кодом из контракта. */
export class ApiCallError extends Error {
  readonly code?: ApiError['code'];

  constructor(failure?: ApiFailure | null) {
    super(failureMessage(failure));
    this.code = failure?.code;
  }
}

/** Сценарий выбирается по коду: служебный текст неизвестного кода не показываем. */
export function failureMessage(failure?: ApiFailure | null): string {
  if (failure?.code && KNOWN_CODES.includes(failure.code) && failure.message) {
    return failure.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}

/** Время недоступно (занято, вне сетки/окна/рабочих часов) — это 409. */
export function isConflict(error: unknown): boolean {
  return error instanceof ApiCallError && !!error.code && CONFLICT_CODES.has(error.code);
}

export async function fetchMeetingTypes(): Promise<MeetingType[]> {
  const { data, error } = await apiListMeetingTypes();
  if (error) {
    throw new ApiCallError(error);
  }
  return data ?? [];
}

export async function createMeetingType(input: MeetingTypeInput): Promise<MeetingType> {
  const { data, error } = await apiCreateMeetingType({ body: input });
  if (error) {
    throw new ApiCallError(error);
  }
  if (data === undefined) {
    throw new ApiCallError(null);
  }
  return data;
}

export async function fetchSlots(meetingTypeId: string): Promise<Slot[]> {
  const { data, error } = await apiListAvailableSlots({ query: { meetingTypeId } });
  if (error) {
    throw new ApiCallError(error);
  }
  return data ?? [];
}

export async function createBooking(input: BookingInput): Promise<Booking> {
  const { data, error } = await apiCreateBooking({ body: input });
  if (error) {
    throw new ApiCallError(error);
  }
  if (data === undefined) {
    throw new ApiCallError(null);
  }
  return data;
}

export async function fetchBookings(): Promise<Booking[]> {
  const { data, error } = await apiListBookings();
  if (error) {
    throw new ApiCallError(error);
  }
  return data ?? [];
}
