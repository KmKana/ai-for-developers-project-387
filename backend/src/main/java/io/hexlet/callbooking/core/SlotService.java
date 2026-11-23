package io.hexlet.callbooking.core;

import io.hexlet.callbooking.api.dto.SlotDto;
import io.hexlet.callbooking.store.Booking;
import io.hexlet.callbooking.store.BookingOverlap;
import io.hexlet.callbooking.store.InMemoryStore;
import io.hexlet.callbooking.store.MeetingType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Вычисление свободных слотов: рабочие дни в окне записи, старты на 30-минутной сетке, длительность
 * слота = длительности типа, пересечения с существующими записями исключены.
 */
@Service
public class SlotService {

  private final InMemoryStore store;
  private final BookingRules rules;

  public SlotService(InMemoryStore store, BookingRules rules) {
    this.store = store;
    this.rules = rules;
  }

  public List<SlotDto> availableSlots(String meetingTypeId) {
    MeetingType type = store.findMeetingType(meetingTypeId);
    if (type == null) {
      throw new ApiRequestException(
          400, ApiErrorCode.MEETING_TYPE_NOT_FOUND, "Тип встречи не найден: " + meetingTypeId);
    }
    long durationMinutes = type.durationMinutes();
    LocalDate from = rules.currentWindowStart();
    LocalDate to = rules.currentWindowEndInclusive();
    List<SlotDto> slots = new ArrayList<>();
    // Один снимок Записей на запрос под монитором хранилища: просмотр Слотов ничего не бронирует.
    List<Booking> existing = store.upcomingBookings();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (!BookingRules.WORK_DAYS.contains(day.getDayOfWeek())) {
        continue;
      }
      for (int startMinutes = BookingRules.WORK_START_MINUTES;
          startMinutes + durationMinutes <= BookingRules.WORK_END_MINUTES;
          startMinutes += BookingRules.STEP_MINUTES) {
        OffsetDateTime start =
            day.atTime(LocalTime.ofSecondOfDay(startMinutes * 60L))
                .atZone(BookingRules.ZONE)
                .toOffsetDateTime();
        OffsetDateTime end = start.plus(Duration.ofMinutes(durationMinutes));
        if (BookingOverlap.conflict(existing, start.toInstant(), end.toInstant()).isEmpty()) {
          slots.add(new SlotDto(start, end));
        }
      }
    }
    return slots;
  }
}
