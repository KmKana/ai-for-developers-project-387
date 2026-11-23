package io.hexlet.callbooking.core;

import io.hexlet.callbooking.api.dto.BookingDto;
import io.hexlet.callbooking.api.dto.BookingInputDto;
import io.hexlet.callbooking.store.Booking;
import io.hexlet.callbooking.store.InMemoryStore;
import io.hexlet.callbooking.store.MeetingType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Запись гостя на свободный слот. */
@Service
public class BookingService {

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /** Лимит темы в кодовых точках Unicode — та же единица, что у maxLength в OpenAPI. */
  private static final int TOPIC_MAX_CODE_POINTS = 300;

  private static final String TOPIC_TOO_LONG_MESSAGE = "Тема разговора не длиннее 300 символов";

  private final InMemoryStore store;
  private final BookingRules rules;

  public BookingService(InMemoryStore store, BookingRules rules) {
    this.store = store;
    this.rules = rules;
  }

  public BookingDto book(BookingInputDto input) {
    String meetingTypeId = input.meetingTypeId() == null ? "" : input.meetingTypeId().trim();
    if (meetingTypeId.isEmpty()) {
      throw new ApiRequestException(400, ApiErrorCode.VALIDATION_ERROR, "meetingTypeId обязателен");
    }
    MeetingType type = store.findMeetingType(meetingTypeId);
    if (type == null) {
      throw new ApiRequestException(
          400,
          ApiErrorCode.MEETING_TYPE_NOT_FOUND,
          "Тип встречи не найден: " + input.meetingTypeId());
    }
    if (input.start() == null) {
      throw new ApiRequestException(400, ApiErrorCode.VALIDATION_ERROR, "Время начала обязательно");
    }
    String guestName = input.guestName() == null ? "" : input.guestName().trim();
    if (guestName.isEmpty()) {
      throw new ApiRequestException(400, ApiErrorCode.VALIDATION_ERROR, "Имя гостя обязательно");
    }
    String guestEmail = input.guestEmail() == null ? "" : input.guestEmail().trim();
    if (guestEmail.isEmpty() || !EMAIL.matcher(guestEmail).matches()) {
      throw new ApiRequestException(400, ApiErrorCode.VALIDATION_ERROR, "Некорректный email гостя");
    }

    Instant startInstant = input.start().toInstant();
    long durationMinutes = type.durationMinutes();
    Instant endInstant = startInstant.plus(Duration.ofMinutes(durationMinutes));

    ZonedDateTime localStart = startInstant.atZone(BookingRules.ZONE);
    LocalDate date = localStart.toLocalDate();
    LocalTime time = localStart.toLocalTime();
    int startMinutes = time.getHour() * 60 + time.getMinute();

    if (date.isBefore(rules.currentWindowStart())
        || date.isAfter(rules.currentWindowEndInclusive())) {
      throw new ApiRequestException(409, ApiErrorCode.OUT_OF_WINDOW);
    }
    if (!BookingRules.WORK_DAYS.contains(date.getDayOfWeek())) {
      throw new ApiRequestException(409, ApiErrorCode.OUTSIDE_WORKING_HOURS);
    }
    if (startMinutes < BookingRules.WORK_START_MINUTES
        || startMinutes + durationMinutes > BookingRules.WORK_END_MINUTES) {
      throw new ApiRequestException(409, ApiErrorCode.OUTSIDE_WORKING_HOURS);
    }
    boolean onGrid =
        (startMinutes - BookingRules.WORK_START_MINUTES) % BookingRules.STEP_MINUTES == 0
            && time.getSecond() == 0
            && time.getNano() == 0;
    if (!onGrid) {
      throw new ApiRequestException(409, ApiErrorCode.OFF_GRID);
    }

    // Тема проверяется после календарных правил: запрос с некорректным Слотом
    // по-прежнему получает 409 (например, OFF_GRID), а не 400 из-за длины темы.
    String topic = normalizeTopic(input.topic());

    // Единственная проверка доступности выполняется под монитором хранилища вместе с созданием
    // Записи: гонка между проверкой и добавлением исключена.
    var result =
        store.tryAddBooking(
            type.id(), type.name(), startInstant, endInstant, guestName, guestEmail, topic);
    if (result.conflict() != null) {
      throw slotConflict();
    }
    return toDto(result.created());
  }

  /**
   * Тема необязательна: пробелы по краям обрезаются, пустая строка равнозначна отсутствию темы.
   * Длина проверяется после обрезки в кодовых точках Unicode, как {@code maxLength} в контракте.
   * Обрезка учитывает Unicode-пробелы, которые {@link String#trim()} не покрывает (NBSP, U+2003,
   * U+FEFF): иначе тема из одних таких символов считалась бы непустой.
   */
  private static String normalizeTopic(String topic) {
    if (topic == null) {
      return null;
    }
    String trimmed = trimTopicEdges(topic);
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.codePointCount(0, trimmed.length()) > TOPIC_MAX_CODE_POINTS) {
      throw new ApiRequestException(400, ApiErrorCode.VALIDATION_ERROR, TOPIC_TOO_LONG_MESSAGE);
    }
    return trimmed;
  }

  /** Обрезает Unicode-пробелы по краям, не разрезая суррогатные пары. */
  private static String trimTopicEdges(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      int codePoint = value.codePointAt(start);
      if (!isTopicWhitespace(codePoint)) {
        break;
      }
      start += Character.charCount(codePoint);
    }
    while (end > start) {
      int codePoint = value.codePointBefore(end);
      if (!isTopicWhitespace(codePoint)) {
        break;
      }
      end -= Character.charCount(codePoint);
    }
    return value.substring(start, end);
  }

  /**
   * Пробелы, которые JavaScript {@code trim()} и Unicode считают краями строки. {@link
   * String#strip()} не покрывает NBSP и U+FEFF, поэтому проверяем явно.
   */
  private static boolean isTopicWhitespace(int codePoint) {
    return Character.isWhitespace(codePoint)
        || Character.isSpaceChar(codePoint)
        || codePoint == 0xFEFF;
  }

  private static ApiRequestException slotConflict() {
    return new ApiRequestException(409, ApiErrorCode.SLOT_CONFLICT);
  }

  public java.util.List<BookingDto> upcoming() {
    return store.upcomingBookings().stream().map(this::toDto).toList();
  }

  private BookingDto toDto(Booking booking) {
    return new BookingDto(
        booking.id(),
        booking.meetingTypeId(),
        booking.meetingTypeName(),
        OffsetDateTime.ofInstant(booking.start(), BookingRules.ZONE),
        OffsetDateTime.ofInstant(booking.end(), BookingRules.ZONE),
        booking.guestName(),
        booking.guestEmail(),
        booking.topic(),
        booking.createdAt());
  }
}
