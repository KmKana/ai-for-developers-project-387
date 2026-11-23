const TIME_ZONE = 'Asia/Almaty';

const ALMATY_CALENDAR = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

function almatyDateKey(now: Date): string {
  const parts = ALMATY_CALENDAR.formatToParts(now);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}

/** Окно записи: 14 календарных дней, начиная с завтрашнего дня по Asia/Almaty. */
export function bookingWindowDates(now: Date = new Date()): string[] {
  const [year, month, day] = almatyDateKey(now).split('-').map(Number);
  const first = Date.UTC(year, month - 1, day + 1);
  return Array.from({ length: 14 }, (_, index) =>
    new Date(first + index * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
  );
}

/** Время начала слота, например «09:30». */
export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: TIME_ZONE,
  }).format(new Date(iso));
}

/** Части дня для чипа в календаре. */
export function dayParts(iso: string): { weekday: string; day: string; month: string } {
  const date = new Date(iso);
  return {
    weekday: new Intl.DateTimeFormat('ru-RU', { weekday: 'short', timeZone: TIME_ZONE }).format(
      date,
    ),
    day: new Intl.DateTimeFormat('ru-RU', { day: 'numeric', timeZone: TIME_ZONE }).format(date),
    month: new Intl.DateTimeFormat('ru-RU', { month: 'short', timeZone: TIME_ZONE }).format(date),
  };
}

/** Дата и время встречи для списков, например «21 ноября, 10:00». */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: TIME_ZONE,
  }).format(new Date(iso));
}

/** Человекочитаемая длительность: 30 мин, 1 ч, 1 ч 15 мин. */
export function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} мин`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} ч` : `${hours} ч ${rest} мин`;
}

/** Длительность Слота в минутах из ответа SDK. */
export function slotDurationMinutes(slot: { start: string; end: string }): number {
  return Math.round((new Date(slot.end).getTime() - new Date(slot.start).getTime()) / 60_000);
}

/** Календарный день слота: префикс ISO-даты (слоты приходят в поясе владельца). */
export function dayKey(iso: string): string {
  return iso.slice(0, 10);
}

/**
 * Все 14 календарных дней окна записи: завтра и ещё 13 дней по Asia/Almaty.
 * Выходные тоже входят в окно: Слотов для них нет, но дни видны Гостю.
 */
export function bookingWindowDays(now: Date = new Date()): string[] {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value);
  const tomorrow = Date.UTC(value('year'), value('month') - 1, value('day') + 1);

  return Array.from({ length: 14 }, (_, offset) =>
    new Date(tomorrow + offset * 86_400_000).toISOString().slice(0, 10),
  );
}

/** Выходной ли календарный день (YYYY-MM-DD). */
export function isWeekend(dateStr: string): boolean {
  const [year, month, day] = dateStr.split('-').map(Number);
  const weekday = new Date(year, month - 1, day).getDay();
  return weekday === 0 || weekday === 6;
}
