import { vi } from 'vitest';

import type * as Sdk from '../sdk';

/** Имена функций сгенерированного SDK, которые подменяются в компонентных тестах. */
export type SdkFunctionName = keyof typeof Sdk;

/**
 * Общая фабрика для vi.mock('../sdk'): настоящий модуль сохраняется, а нужные
 * функции заменяются на vi.fn(). Вызов vi.mock остаётся в тесте, потому что
 * Vitest поднимает его наверх файла.
 */
export async function mockSdkModule(
  importOriginal: () => Promise<unknown>,
  names: readonly SdkFunctionName[],
): Promise<typeof Sdk> {
  const actual = (await importOriginal()) as typeof Sdk;
  return {
    ...actual,
    ...Object.fromEntries(names.map((name) => [name, vi.fn()])),
  } as typeof Sdk;
}

/** Успешный ответ сгенерированного SDK. */
export function response<T>(data: T, url: string, status = 200) {
  return {
    data,
    error: undefined,
    request: new Request(url),
    response: new Response(JSON.stringify(data), { status }),
  };
}

/** Ответ ошибки сгенерированного SDK. */
export function failure(error: Sdk.ApiError, url: string, status: number) {
  return {
    data: undefined,
    error,
    request: new Request(url),
    response: new Response(JSON.stringify(error), { status }),
  };
}
