package io.hexlet.callbooking.core;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Правила записи владельца. Зафиксированы в спецификации API (api/tsp/main.tsp) и в README.md. */
@Component
public class BookingRules {

  /** Часовой пояс владельца. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Almaty");

  /** Начало рабочего дня, минуты от полуночи (09:00). */
  public static final int WORK_START_MINUTES = 9 * 60;

  /** Конец рабочего дня, минуты от полуночи (18:00). */
  public static final int WORK_END_MINUTES = 18 * 60;

  /** Шаг доступных стартов, минуты (30-минутная сетка). */
  public static final int STEP_MINUTES = 30;

  /** Окно записи в календарных днях. */
  public static final int WINDOW_DAYS = 14;

  /** Минимальная длительность типа встречи, минуты. */
  public static final int MIN_TYPE_DURATION = 5;

  /** Максимальная длительность типа встречи, минуты. */
  public static final int MAX_TYPE_DURATION = 480;

  /** Рабочие дни владельца: понедельник–пятница. */
  public static final Set<DayOfWeek> WORK_DAYS =
      Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);

  private final Clock clock;

  public BookingRules(Clock clock) {
    this.clock = clock;
  }

  /** Первый день окна записи для текущего запроса: завтра по времени владельца. */
  public LocalDate currentWindowStart() {
    return clock.instant().atZone(ZONE).toLocalDate().plusDays(1);
  }

  /** Последний день окна записи включительно, пересчитанный для текущего запроса. */
  public LocalDate currentWindowEndInclusive() {
    return currentWindowStart().plusDays(WINDOW_DAYS - 1);
  }
}
