package io.hexlet.callbooking.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Единственное правило пересечения Слотов: [start, end) полуоткрытые.
 *
 * <p>Стык разрешён: новая встреча может начинаться в момент окончания предыдущей. Проверка не
 * зависит от Типа встречи: календарь Владельца один на все типы.
 */
public final class BookingOverlap {

  private BookingOverlap() {}

  /** Пересекается ли существующая Запись со Слотом [start, end). */
  public static boolean overlaps(Booking existing, Instant start, Instant end) {
    return existing.start().isBefore(end) && start.isBefore(existing.end());
  }

  /** Первая конфликтующая Запись или пусто, если Слот свободен. */
  public static Optional<Booking> conflict(List<Booking> bookings, Instant start, Instant end) {
    return bookings.stream().filter(b -> overlaps(b, start, end)).findFirst();
  }
}
