package io.hexlet.callbooking.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Календарные причины недоступности времени остаются отдельными кодами 409. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingCalendarErrorApiTest {

  private static final String OFF_GRID_MESSAGE =
      "Начало встречи должно быть на 30-минутной сетке. Выберите другое время.";
  private static final String OUTSIDE_WORKING_HOURS_MESSAGE =
      "Встреча должна проходить в рабочие дни с 09:00 до 18:00 по времени владельца.";
  private static final String OUT_OF_WINDOW_MESSAGE =
      "Запись доступна на ближайшие 14 дней. Выберите другую дату.";

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void offGridStartReturnsItsOwnCodeAndMessage() throws Exception {
    String typeId = createType();

    expectConflict(booking(typeId, iso(2026, 3, 11, 9, 10)), "OFF_GRID", OFF_GRID_MESSAGE);
  }

  @Test
  void timeOutsideWorkingHoursReturnsItsOwnCodeAndMessage() throws Exception {
    String typeId = createType();

    expectConflict(
        booking(typeId, iso(2026, 3, 11, 8, 30)),
        "OUTSIDE_WORKING_HOURS",
        OUTSIDE_WORKING_HOURS_MESSAGE);
    // Суббота внутри окна записи — тоже нерабочее время Владельца.
    expectConflict(
        booking(typeId, iso(2026, 3, 14, 10, 0)),
        "OUTSIDE_WORKING_HOURS",
        OUTSIDE_WORKING_HOURS_MESSAGE);
  }

  @Test
  void dateOutsideFourteenDayWindowReturnsItsOwnCodeAndMessage() throws Exception {
    String typeId = createType();

    // Сегодня недоступно: окно начинается завтра.
    expectConflict(
        booking(typeId, iso(2026, 3, 10, 10, 0)), "OUT_OF_WINDOW", OUT_OF_WINDOW_MESSAGE);
    // 15-й день тоже недоступен: окно заканчивается 24 марта.
    expectConflict(
        booking(typeId, iso(2026, 3, 25, 10, 0)), "OUT_OF_WINDOW", OUT_OF_WINDOW_MESSAGE);
  }

  private void expectConflict(String body, String code, String message) throws Exception {
    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.message").value(message));
  }

  private String booking(String typeId, String start) throws Exception {
    return om.writeValueAsString(
        Map.of(
            "meetingTypeId",
            typeId,
            "start",
            start,
            "guestName",
            "Гость",
            "guestEmail",
            "guest@example.com"));
  }

  private String createType() throws Exception {
    String response =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(
                        om.writeValueAsString(
                            Map.of(
                                "name", "Знакомство",
                                "description", "Короткий созвон",
                                "durationMinutes", 30))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  private static String iso(int year, int month, int day, int hour, int minute) {
    return LocalDate.of(year, month, day)
        .atTime(LocalTime.of(hour, minute))
        .atZone(FixedClockConfig.OWNER_ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
