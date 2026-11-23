package io.hexlet.callbooking.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Календарные правила реализации сверяются со спецификацией: сначала публичные константы, затем
 * поведение API при фиксированном времени. Ожидаемые значения заданы спецификацией, а не
 * вычисляются из тех же констант.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CalendarRulesConsistencyTest {

  /** Значения из спецификации (#12), независимые от реализации. */
  private static final ZoneId SPEC_ZONE = ZoneId.of("Asia/Almaty");

  private static final LocalDate WINDOW_FIRST_DAY = LocalDate.of(2026, 3, 11);
  private static final LocalDate WINDOW_LAST_DAY = LocalDate.of(2026, 3, 24);
  private static final LocalTime SPEC_WORK_START = LocalTime.of(9, 0);
  private static final LocalTime SPEC_WORK_END = LocalTime.of(18, 0);
  private static final int SPEC_STEP_MINUTES = 30;
  private static final int SPEC_MIN_DURATION = 5;
  private static final int SPEC_MAX_DURATION = 480;

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void implementationConstantsMatchTheSpecification() {
    assertThat(BookingRules.ZONE).isEqualTo(SPEC_ZONE);
    assertThat(BookingRules.WORK_DAYS)
        .containsExactlyInAnyOrder(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);
    assertThat(BookingRules.WORK_START_MINUTES).isEqualTo(SPEC_WORK_START.getHour() * 60);
    assertThat(BookingRules.WORK_END_MINUTES).isEqualTo(SPEC_WORK_END.getHour() * 60);
    assertThat(BookingRules.STEP_MINUTES).isEqualTo(SPEC_STEP_MINUTES);
    assertThat(BookingRules.WINDOW_DAYS)
        .isEqualTo(
            java.time.temporal.ChronoUnit.DAYS.between(WINDOW_FIRST_DAY, WINDOW_LAST_DAY) + 1);
    assertThat(BookingRules.MIN_TYPE_DURATION).isEqualTo(SPEC_MIN_DURATION);
    assertThat(BookingRules.MAX_TYPE_DURATION).isEqualTo(SPEC_MAX_DURATION);
  }

  @Test
  void slotsApiExposesOnlySpecWorkingDaysGridAndWindow() throws Exception {
    String typeId = createType(30);

    JsonNode slots = slots(typeId);
    assertThat(slots).hasSize(180); // 10 рабочих дней в окне 11–24 марта × 18 стартов

    List<String> firstDayStarts = new ArrayList<>();
    for (JsonNode slot : slots) {
      OffsetDateTime start = OffsetDateTime.parse(slot.path("start").asText());
      OffsetDateTime end = OffsetDateTime.parse(slot.path("end").asText());
      LocalDate date = start.atZoneSameInstant(SPEC_ZONE).toLocalDate();
      LocalTime time = start.atZoneSameInstant(SPEC_ZONE).toLocalTime();

      assertThat(date).isBetween(WINDOW_FIRST_DAY, WINDOW_LAST_DAY);
      assertThat(date.getDayOfWeek())
          .isIn(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY);
      assertThat(time).isBetween(SPEC_WORK_START, SPEC_WORK_END);
      assertThat(time.getSecond()).isZero();
      assertThat(time.getNano()).isZero();
      assertThat(Duration.between(start, end)).isEqualTo(Duration.ofMinutes(30));
      if (date.equals(WINDOW_FIRST_DAY)) {
        firstDayStarts.add(slot.path("start").asText());
      }
    }

    assertThat(firstDayStarts).containsExactlyElementsOf(expectedFirstDayStarts(30));
  }

  @Test
  void durationBoundsAndLastStartAreSameInContractAndApi() throws Exception {
    Map<String, Integer> typesByDuration =
        Map.of(
            createType(SPEC_MIN_DURATION), SPEC_MIN_DURATION,
            createType(SPEC_MAX_DURATION), SPEC_MAX_DURATION,
            createType(45), 45);

    // Шаг 30 минут от 09:00: последний старт 17:30 и для 5 минут, и для 30.
    assertThat(lastStartOnFirstDay(typeIdFor(typesByDuration, SPEC_MIN_DURATION)))
        .isEqualTo(iso(WINDOW_FIRST_DAY, 17, 30));
    // Встреча 10:00–18:00 целиком укладывается в рабочий день, поэтому последний старт 10:00.
    assertThat(lastStartOnFirstDay(typeIdFor(typesByDuration, SPEC_MAX_DURATION)))
        .isEqualTo(iso(WINDOW_FIRST_DAY, 10, 0));
    assertThat(lastStartOnFirstDay(typeIdFor(typesByDuration, 45)))
        .isEqualTo(iso(WINDOW_FIRST_DAY, 17, 0));
    for (Map.Entry<String, Integer> type : typesByDuration.entrySet()) {
      for (JsonNode slot : slots(type.getKey())) {
        assertThat(
                Duration.between(
                    OffsetDateTime.parse(slot.path("start").asText()),
                    OffsetDateTime.parse(slot.path("end").asText())))
            .isEqualTo(Duration.ofMinutes(type.getValue()));
      }
    }

    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(typePayload(SPEC_MIN_DURATION - 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(typePayload(SPEC_MAX_DURATION + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private String createType(int durationMinutes) throws Exception {
    String response =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(typePayload(durationMinutes)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  private String typePayload(int durationMinutes) throws Exception {
    return om.writeValueAsString(Map.of("name", "Знакомство", "durationMinutes", durationMinutes));
  }

  private JsonNode slots(String typeId) throws Exception {
    String response =
        mvc.perform(get("/api/slots").param("meetingTypeId", typeId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response);
  }

  private String lastStartOnFirstDay(String typeId) throws Exception {
    List<String> starts = new ArrayList<>();
    for (JsonNode slot : slots(typeId)) {
      OffsetDateTime start = OffsetDateTime.parse(slot.path("start").asText());
      if (start.atZoneSameInstant(SPEC_ZONE).toLocalDate().equals(WINDOW_FIRST_DAY)) {
        starts.add(slot.path("start").asText());
      }
    }
    return starts.get(starts.size() - 1);
  }

  private static String typeIdFor(Map<String, Integer> typesByDuration, int durationMinutes) {
    return typesByDuration.entrySet().stream()
        .filter(entry -> entry.getValue() == durationMinutes)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow();
  }

  private static List<String> expectedFirstDayStarts(int durationMinutes) {
    List<String> starts = new ArrayList<>();
    for (int minutes = SPEC_WORK_START.getHour() * 60;
        minutes + durationMinutes <= SPEC_WORK_END.getHour() * 60;
        minutes += SPEC_STEP_MINUTES) {
      starts.add(iso(WINDOW_FIRST_DAY, minutes / 60, minutes % 60));
    }
    return starts;
  }

  private static String iso(LocalDate date, int hour, int minute) {
    return date.atTime(LocalTime.of(hour, minute))
        .atZone(SPEC_ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
