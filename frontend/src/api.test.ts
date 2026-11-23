import { describe, expect, it } from 'vitest';

import { ApiCallError, failureMessage, isConflict } from './api';
import { ApiErrorCode } from './sdk';

const FALLBACK = 'Что-то пошло не так. Попробуйте ещё раз.';

describe('Обработка ошибок API', () => {
  it('показывает сообщения для всех кодов из сгенерированного SDK', () => {
    for (const code of Object.values(ApiErrorCode)) {
      expect(failureMessage({ code, message: `Сообщение ${code}` })).toBe(`Сообщение ${code}`);
    }

    expect(failureMessage({ code: 'UNKNOWN' as ApiErrorCode, message: 'Служебное' })).toBe(
      FALLBACK,
    );
    expect(failureMessage(null)).toBe(FALLBACK);
  });

  it('считает конфликтом только коды 409 из контракта', () => {
    const conflictCodes = new Set<ApiErrorCode>([
      ApiErrorCode.SLOT_CONFLICT,
      ApiErrorCode.OFF_GRID,
      ApiErrorCode.OUTSIDE_WORKING_HOURS,
      ApiErrorCode.OUT_OF_WINDOW,
    ]);

    for (const code of Object.values(ApiErrorCode)) {
      expect(isConflict(new ApiCallError({ code, message: 'Недоступно' }))).toBe(
        conflictCodes.has(code),
      );
    }
    expect(isConflict(new Error('Недоступно'))).toBe(false);
  });
});
