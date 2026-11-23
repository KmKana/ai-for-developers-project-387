package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Защита календаря Владельца от дублирования Записей и двойной записи на один Слот: Слоты [start,
 * end) полуоткрытые.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingConflictApiTest {

  private static final String SLOT_CONFLICT_MESSAGE =
      "Выбранное время уже занято. Выберите другой слот";

  /** Первый день окна записи при фиксированном времени 10 марта 2026 года. */
  private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 11);

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void sameStartReturnsSlotConflictAndListingDoesNotReserveTime() throws Exception {
    String typeId = createType("Знакомство", 30);
    String start = iso(10, 0);

    assertThat(slotStarts(typeId)).contains(start);
    // Просмотр Слотов дважды не должен занимать время: следующая Запись создаётся успешно.
    slotStarts(typeId);

    book(typeId, start, "Гость 1", "guest1@example.com").andExpect(status().isCreated());

    expectSlotConflict(book(typeId, start, "Гость 2", "guest2@example.com"));
    assertThat(slotStarts(typeId)).doesNotContain(start);
  }

  @Test
  void partialOverlapWithSameTypeReturnsSlotConflict() throws Exception {
    String hourType = createType("Час обзора", 60);
    String halfHourType = createType("Знакомство", 30);

    book(hourType, iso(10, 0), "Гость 1", "guest1@example.com").andExpect(status().isCreated());
    // 10:30–11:00 целиком внутри занятого Слота 10:00–11:00.
    expectSlotConflict(book(halfHourType, iso(10, 30), "Гость 2", "guest2@example.com"));
  }

  @Test
  void differentTypesCannotOverlap() throws Exception {
    String hourType = createType("Час обзора", 60);
    String halfHourType = createType("Знакомство", 30);

    book(hourType, iso(12, 0), "Гость 1", "guest1@example.com").andExpect(status().isCreated());
    // 12:30–13:00 пересекает чужую встречу, хотя это другой Тип встречи.
    expectSlotConflict(book(halfHourType, iso(12, 30), "Гость 2", "guest2@example.com"));
  }

  @Test
  void adjoiningBookingsAreAllowedOnBothSides() throws Exception {
    String hourType = createType("Час обзора", 60);
    String halfHourType = createType("Знакомство", 30);

    book(hourType, iso(10, 0), "Гость 1", "guest1@example.com").andExpect(status().isCreated());
    book(halfHourType, iso(9, 30), "Гость 2", "guest2@example.com").andExpect(status().isCreated());
    book(halfHourType, iso(11, 0), "Гость 3", "guest3@example.com").andExpect(status().isCreated());

    String response =
        mvc.perform(get("/api/bookings"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(response)).hasSize(3);
    assertThat(objectMapper.readTree(response).findValuesAsText("start"))
        .containsExactly(iso(9, 30), iso(10, 0), iso(11, 0));
  }

  @Test
  void conflictIsDetectedAgainAfterSlotWasListed() throws Exception {
    String typeId = createType("Знакомство", 30);
    String start = (String) slotStarts(typeId).get(0);

    book(typeId, start, "Гость 1", "guest1@example.com").andExpect(status().isCreated());
    // POST заново проверяет доступность, даже если Слот был в ранее полученном списке.
    expectSlotConflict(book(typeId, start, "Гость 2", "guest2@example.com"));
  }

  private void expectSlotConflict(ResultActions result) throws Exception {
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"))
        .andExpect(jsonPath("$.message").value(SLOT_CONFLICT_MESSAGE));
  }

  private ResultActions book(String typeId, String start, String guestName, String guestEmail)
      throws Exception {
    return ApiTestFixtures.book(mvc, typeId, start, guestName, guestEmail);
  }

  private String createType(String name, int durationMinutes) throws Exception {
    return ApiTestFixtures.createType(mvc, objectMapper, name, durationMinutes);
  }

  private List<String> slotStarts(String typeId) throws Exception {
    String response =
        mvc.perform(get("/api/slots").queryParam("meetingTypeId", typeId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).findValuesAsText("start");
  }

  private static String iso(int hour, int minute) {
    return ApiTestFixtures.iso(FIRST_DAY.toString(), hour, minute);
  }
}
