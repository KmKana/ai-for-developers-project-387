package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.BookingRules;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Сквозные проверки API по контракту: создание типа встречи, свободные Слоты, запись, пересечения
 * разных типов и список предстоящих встреч владельца.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiBookingFlowTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Autowired private Clock clock;

  @Autowired private BookingRules rules;

  private String createType(String name, String description, int durationMinutes) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(
                        om.writeValueAsString(
                            Map.of(
                                "name", name,
                                "description", description,
                                "durationMinutes", durationMinutes))))
            .andExpect(status().isCreated())
            .andReturn();
    return om.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private List<Map<String, Object>> slots(String typeId) throws Exception {
    MvcResult result =
        mvc.perform(get("/api/slots").queryParam("meetingTypeId", typeId))
            .andExpect(status().isOk())
            .andReturn();
    return om.readValue(result.getResponse().getContentAsString(), List.class);
  }

  /** Первый рабочий день окна записи (окно всегда начинается с завтра). */
  private LocalDate firstWorkingDay() {
    LocalDate day = rules.currentWindowStart();
    while (!BookingRules.WORK_DAYS.contains(day.getDayOfWeek())) {
      day = day.plusDays(1);
    }
    return day;
  }

  private static String iso(LocalDate date, int hour, int minute) {
    return date.atTime(LocalTime.of(hour, minute))
        .atZone(BookingRules.ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  @Test
  void ownerCreatesTypeAndItAppearsInCatalog() throws Exception {
    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "name", "Знакомство",
                            "description", "Короткий созвон о вашем проекте",
                            "durationMinutes", 30))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Знакомство"))
        .andExpect(jsonPath("$.description").value("Короткий созвон о вашем проекте"))
        .andExpect(jsonPath("$.durationMinutes").value(30));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Знакомство"))
        .andExpect(jsonPath("$[0].durationMinutes").value(30));
  }

  @Test
  void createTypeValidation() throws Exception {
    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"  \",\"durationMinutes\":30}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of("name", "Очень длинная встреча", "durationMinutes", 481))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of("name", "Слишком короткая", "durationMinutes", 4))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content("{\"durationMinutes\":30}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void slotsAreOnGridWithinWorkingHoursAndWindow() throws Exception {
    String typeId = createType("Обзор 30 минут", "Разбор проекта", 30);
    List<Map<String, Object>> slots = slots(typeId);
    assertThat(slots).isNotEmpty();

    LocalDate windowStart = rules.currentWindowStart();
    LocalDate windowEnd = rules.currentWindowEndInclusive();
    for (Map<String, Object> slot : slots) {
      OffsetDateTime start = OffsetDateTime.parse((String) slot.get("start"));
      OffsetDateTime end = OffsetDateTime.parse((String) slot.get("end"));
      LocalDate date = start.atZoneSameInstant(BookingRules.ZONE).toLocalDate();
      LocalTime time = start.atZoneSameInstant(BookingRules.ZONE).toLocalTime();
      int minutes = time.getHour() * 60 + time.getMinute();

      assertThat(date).isBetween(windowStart, windowEnd);
      assertThat(BookingRules.WORK_DAYS.contains(date.getDayOfWeek())).isTrue();
      assertThat(minutes % 30).isZero();
      assertThat(minutes).isGreaterThanOrEqualTo(BookingRules.WORK_START_MINUTES);
      assertThat(end.toInstant()).isAfter(start.toInstant());
    }
  }

  @Test
  void slotsForLongTypeFitIntoWorkingHours() throws Exception {
    // 45-минутный тип: последний старт 17:00 (17:00 + 45 = 17:45 <= 18:00)
    String typeId = createType("Углублённый разбор", "45 минут", 45);
    List<Map<String, Object>> slots = slots(typeId);
    LocalTime last =
        slots.stream()
            .map(s -> OffsetDateTime.parse((String) s.get("start")))
            .map(t -> t.atZoneSameInstant(BookingRules.ZONE).toLocalTime())
            .max(LocalTime::compareTo)
            .orElseThrow();
    assertThat(last).isEqualTo(LocalTime.of(17, 0));
  }

  @Test
  void slotsForUnknownTypeAreRejected() throws Exception {
    mvc.perform(get("/api/slots").queryParam("meetingTypeId", "no-such-type"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEETING_TYPE_NOT_FOUND"));
  }

  @Test
  void guestBooksSlotAndItAppearsInOwnerList() throws Exception {
    String typeId = createType("Знакомство", "Короткий созвон", 30);
    String start = (String) slots(typeId).get(0).get("start");

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            typeId,
                            "start",
                            start,
                            "guestName",
                            "Алексей Иванов",
                            "guestEmail",
                            "alex@example.com"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.meetingTypeId").value(typeId))
        .andExpect(jsonPath("$.meetingTypeName").value("Знакомство"))
        .andExpect(jsonPath("$.guestName").value("Алексей Иванов"))
        .andExpect(jsonPath("$.guestEmail").value("alex@example.com"));

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].guestName").value("Алексей Иванов"))
        .andExpect(jsonPath("$[0].meetingTypeName").value("Знакомство"));
  }

  @Test
  void sameSlotCannotBeBookedTwice() throws Exception {
    String typeId = createType("Знакомство", "Короткий созвон", 30);
    String start = (String) slots(typeId).get(0).get("start");

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            typeId,
                            "start",
                            start,
                            "guestName",
                            "Гость 1",
                            "guestEmail",
                            "guest1@example.com"))))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            typeId,
                            "start",
                            start,
                            "guestName",
                            "Гость 2",
                            "guestEmail",
                            "guest2@example.com"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"));
  }

  @Test
  void differentTypesCannotOverlapPartiallyButMayTouch() throws Exception {
    String hourType = createType("Час обзора", "60 минут", 60);
    String halfType = createType("Знакомство", "30 минут", 30);
    LocalDate day = firstWorkingDay();

    // Занимаем 10:00–11:00 часовой встречей.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            hourType,
                            "start",
                            iso(day, 10, 0),
                            "guestName",
                            "Гость",
                            "guestEmail",
                            "guest@example.com"))))
        .andExpect(status().isCreated());

    // 10:30–11:00 частично пересекается — отказ.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            halfType,
                            "start",
                            iso(day, 10, 30),
                            "guestName",
                            "Гость",
                            "guestEmail",
                            "guest@example.com"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"));

    // 09:30–10:00 примыкает к началу — разрешено.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            halfType,
                            "start",
                            iso(day, 9, 30),
                            "guestName",
                            "Гость",
                            "guestEmail",
                            "guest@example.com"))))
        .andExpect(status().isCreated());

    // 11:00–11:30 начинается в момент окончания предыдущей — разрешено.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            halfType,
                            "start",
                            iso(day, 11, 0),
                            "guestName",
                            "Гость",
                            "guestEmail",
                            "guest@example.com"))))
        .andExpect(status().isCreated());
  }

  @Test
  void bookingRejectsTimesOutsideRules() throws Exception {
    String typeId = createType("Знакомство", "30 минут", 30);
    LocalDate windowEnd = rules.currentWindowEndInclusive();

    // Не на 30-минутной сетке.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, iso(firstWorkingDay(), 9, 10))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OFF_GRID"));

    // Раньше рабочих часов.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, iso(firstWorkingDay(), 8, 30))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));

    // После рабочих часов (17:30 + 30 минут выходит за 18:00? нет — ровно 18:00,
    // поэтому берём 60-минутный тип: 17:30 + 60 = 18:30).
    String hourType = createType("Час обзора", "60 минут", 60);
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(hourType, iso(firstWorkingDay(), 17, 30))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));

    // Сегодняшний день — вне окна (окно начинается с завтрашнего).
    assertThat(rules.currentWindowStart()).isEqualTo(LocalDate.now(clock).plusDays(1));
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, iso(LocalDate.now(clock), 10, 0))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUT_OF_WINDOW"));

    // На день позже окна.
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, iso(windowEnd.plusDays(1), 10, 0))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUT_OF_WINDOW"));

    // Выходной день.
    LocalDate weekend = rules.currentWindowStart();
    while (BookingRules.WORK_DAYS.contains(weekend.getDayOfWeek())) {
      weekend = weekend.plusDays(1);
    }
    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, iso(weekend, 10, 0))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
  }

  @Test
  void bookingInputValidation() throws Exception {
    String typeId = createType("Знакомство", "30 минут", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            typeId,
                            "start",
                            iso(firstWorkingDay(), 10, 0),
                            "guestName",
                            "Гость",
                            "guestEmail",
                            "не-email"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId",
                            typeId,
                            "start",
                            iso(firstWorkingDay(), 10, 0),
                            "guestName",
                            " ",
                            "guestEmail",
                            "guest@example.com"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking("unknown-type", iso(firstWorkingDay(), 10, 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEETING_TYPE_NOT_FOUND"));

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of(
                            "meetingTypeId", typeId,
                            "start", "не-дата",
                            "guestName", "Гость",
                            "guestEmail", "guest@example.com"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void bookedSlotDisappearsFromAvailableSlots() throws Exception {
    String typeId = createType("Знакомство", "30 минут", 30);
    List<Map<String, Object>> before = slots(typeId);
    String booked = (String) before.get(0).get("start");

    mvc.perform(
            post("/api/bookings").contentType(APPLICATION_JSON).content(booking(typeId, booked)))
        .andExpect(status().isCreated());

    List<Map<String, Object>> after = slots(typeId);
    assertThat(after).hasSize(before.size() - 1);
    assertThat(after.stream().map(s -> s.get("start"))).doesNotContain(booked);

    // Другой тип тоже не может занять это время: 30-минутная Запись
    // перекрывает первые слоты 60-минутного типа.
    String hourType = createType("Час обзора", "60 минут", 60);
    List<Map<String, Object>> hourSlots = slots(hourType);
    assertThat(hourSlots.stream().map(s -> s.get("start"))).doesNotContain(booked);
  }

  private static String booking(String typeId, String start) {
    return "{\"meetingTypeId\":\""
        + typeId
        + "\",\"start\":\""
        + start
        + "\","
        + "\"guestName\":\"Гость\",\"guestEmail\":\"guest@example.com\"}";
  }
}
