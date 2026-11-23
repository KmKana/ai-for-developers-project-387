package io.hexlet.callbooking.store;

import io.hexlet.callbooking.core.BookingRules;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Хранилище в памяти процесса. Перезапуск сервиса сбрасывает данные.
 *
 * <p>Чтение и изменение Записей сериализуются одним монитором объекта, поэтому два одновременных
 * запроса на одно и то же время не могут оба пройти проверку пересечений: успешным будет только
 * один. Методы чтения возвращают неизменяемые снимки. Гарантия действует только внутри одного
 * процесса; распределённые блокировки не реализованы.
 */
@Component
public class InMemoryStore {

  /** Результат попытки брони: созданная запись или найденный конфликт. */
  public record AddBookingResult(Booking created, Booking conflict) {}

  private final Map<String, MeetingType> meetingTypes = new LinkedHashMap<>();

  private final List<Booking> bookings = new ArrayList<>();

  private final Clock clock;

  public InMemoryStore(Clock clock) {
    this.clock = clock;
  }

  public synchronized MeetingType addMeetingType(
      String name, String description, int durationMinutes) {
    var type = new MeetingType(UUID.randomUUID().toString(), name, description, durationMinutes);
    meetingTypes.put(type.id(), type);
    return type;
  }

  public synchronized MeetingType findMeetingType(String id) {
    return meetingTypes.get(id);
  }

  public synchronized List<MeetingType> listMeetingTypes() {
    return List.copyOf(meetingTypes.values());
  }

  /**
   * Атомарно проверяет пересечение Слота [start, end) с уже существующими Записями и добавляет
   * новую. Частичное наложение запрещено; стык Слотов разрешён (start == чужой end — ок).
   */
  public synchronized AddBookingResult tryAddBooking(
      String meetingTypeId,
      String meetingTypeName,
      Instant start,
      Instant end,
      String guestName,
      String guestEmail) {
    Booking conflict = BookingOverlap.conflict(bookings, start, end).orElse(null);
    if (conflict != null) {
      return new AddBookingResult(null, conflict);
    }
    Booking created =
        new Booking(
            UUID.randomUUID().toString(),
            meetingTypeId,
            meetingTypeName,
            start,
            end,
            guestName,
            guestEmail,
            OffsetDateTime.ofInstant(clock.instant(), BookingRules.ZONE));
    bookings.add(created);
    return new AddBookingResult(created, null);
  }

  /** Неизменяемый снимок предстоящих встреч по всем типам, отсортированный по времени начала. */
  public synchronized List<Booking> upcomingBookings() {
    Instant now = clock.instant();
    return bookings.stream()
        .filter(b -> !b.start().isBefore(now))
        .sorted(java.util.Comparator.comparing(Booking::start))
        .toList();
  }
}
