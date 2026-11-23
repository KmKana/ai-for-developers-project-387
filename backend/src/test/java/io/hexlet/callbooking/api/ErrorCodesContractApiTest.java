package io.hexlet.callbooking.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * По одному API-тесту на каждый код ошибки контракта: статус, {@code code} и {@code message}
 * проверяются через реальные HTTP-ответы Spring Boot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ErrorCodesContractApiTest {

  private static final String FIRST_DAY = "2026-03-11";

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void validationErrorHasBadRequestStatusAndPublicMessage() throws Exception {
    mvc.perform(
            post("/api/meeting-types").contentType(APPLICATION_JSON).content(meetingType("", 30)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("Название типа встречи обязательно"));
  }

  @Test
  void unknownMeetingTypeHasBadRequestStatusAndPublicMessage() throws Exception {
    mvc.perform(get("/api/slots").param("meetingTypeId", "missing-type"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEETING_TYPE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Тип встречи не найден: missing-type"));
  }

  @Test
  void slotConflictHasConflictStatusAndFixedMessage() throws Exception {
    String typeId = createType(30);
    String start = iso(FIRST_DAY, 10, 0);

    book(typeId, start, "Гость 1", "first@example.com").andExpect(status().isCreated());

    book(typeId, start, "Гость 2", "second@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"))
        .andExpect(jsonPath("$.message").value("Выбранное время уже занято. Выберите другой слот"));
  }

  @Test
  void offGridHasConflictStatusAndFixedMessage() throws Exception {
    String typeId = createType(30);

    book(typeId, iso(FIRST_DAY, 9, 10), "Гость", "guest@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OFF_GRID"))
        .andExpect(
            jsonPath("$.message")
                .value("Начало встречи должно быть на 30-минутной сетке. Выберите другое время."));
  }

  @Test
  void outsideWorkingHoursHasConflictStatusAndFixedMessage() throws Exception {
    String typeId = createType(30);

    book(typeId, iso(FIRST_DAY, 8, 30), "Гость", "guest@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Встреча должна проходить в рабочие дни с 09:00 до 18:00 по времени владельца."));
  }

  @Test
  void outOfWindowHasConflictStatusAndFixedMessage() throws Exception {
    String typeId = createType(30);

    // Сегодня (10 марта) ещё недоступно: окно начинается завтра.
    book(typeId, iso("2026-03-10", 10, 0), "Гость", "guest@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUT_OF_WINDOW"))
        .andExpect(
            jsonPath("$.message")
                .value("Запись доступна на ближайшие 14 дней. Выберите другую дату."));
  }

  private ResultActions book(String typeId, String start, String guestName, String guestEmail)
      throws Exception {
    return ApiTestFixtures.book(mvc, typeId, start, guestName, guestEmail);
  }

  private String createType(int durationMinutes) throws Exception {
    return ApiTestFixtures.createType(mvc, om, "Знакомство", durationMinutes);
  }

  private String meetingType(String name, int durationMinutes) throws Exception {
    return ApiTestFixtures.meetingTypeJson(om, name, durationMinutes);
  }

  private static String iso(String date, int hour, int minute) {
    return ApiTestFixtures.iso(date, hour, minute);
  }
}
