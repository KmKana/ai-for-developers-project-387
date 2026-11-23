package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.api.support.OpenApiResponseValidator;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Реальные HTTP-ответы Spring Boot для всех пяти операций проверяются по {@code openapi.yaml}, а не
 * по вручную повторённым ожидаемым JSON-структурам.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OpenApiConformanceTest {

  private static final Path OPENAPI = Path.of("..", "api", "openapi", "openapi.yaml");

  private final OpenApiResponseValidator openApi = new OpenApiResponseValidator(OPENAPI);

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  private String typeId;

  @BeforeEach
  void createTypeForSlotsAndBookings() throws Exception {
    typeId =
        idOf(
            mvc.perform(
                    post("/api/meeting-types")
                        .contentType(APPLICATION_JSON)
                        .content(meetingType("Знакомство", 30)))
                .andExpect(status().isCreated())
                .andReturn());
  }

  @Test
  void contractContainsExactlyTheFiveDocumentedOperations() {
    assertThat(openApi.operationIds())
        .containsExactlyInAnyOrder(
            "Api_listMeetingTypes",
            "Api_createMeetingType",
            "Api_listAvailableSlots",
            "Api_createBooking",
            "Api_listBookings");
  }

  @Test
  void listMeetingTypesResponseConformsToOpenApi() throws Exception {
    MvcResult result = mvc.perform(get("/api/meeting-types")).andReturn();

    openApi.assertConforms("Api_listMeetingTypes", result);
    assertThat(om.readTree(result.getResponse().getContentAsString())).hasSize(1);
  }

  @Test
  void createMeetingTypeSuccessAndValidationErrorConformToOpenApi() throws Exception {
    MvcResult created =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(meetingType("Обзор", 45)))
            .andReturn();
    openApi.assertConforms("Api_createMeetingType", created);

    MvcResult invalid =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(meetingType("", 45)))
            .andReturn();
    openApi.assertConforms("Api_createMeetingType", invalid);
  }

  @Test
  void listAvailableSlotsSuccessAndErrorConformToOpenApi() throws Exception {
    MvcResult slots = mvc.perform(get("/api/slots").param("meetingTypeId", typeId)).andReturn();
    openApi.assertConforms("Api_listAvailableSlots", slots);

    MvcResult unknown =
        mvc.perform(get("/api/slots").param("meetingTypeId", "missing-type")).andReturn();
    openApi.assertConforms("Api_listAvailableSlots", unknown);
  }

  @Test
  void createBookingSuccessAndBothErrorFamiliesConformToOpenApi() throws Exception {
    String start = iso(LocalDate.of(2026, 3, 11), 10, 0);

    MvcResult booking =
        mvc.perform(
                post("/api/bookings").contentType(APPLICATION_JSON).content(booking(typeId, start)))
            .andReturn();
    openApi.assertConforms("Api_createBooking", booking);

    MvcResult validationError =
        mvc.perform(
                post("/api/bookings")
                    .contentType(APPLICATION_JSON)
                    .content(booking(typeId, start, "guest@example.com", "")))
            .andReturn();
    openApi.assertConforms("Api_createBooking", validationError);

    MvcResult conflict =
        mvc.perform(
                post("/api/bookings").contentType(APPLICATION_JSON).content(booking(typeId, start)))
            .andReturn();
    openApi.assertConforms("Api_createBooking", conflict);
  }

  @Test
  void listBookingsResponseConformsToOpenApi() throws Exception {
    mvc.perform(
        post("/api/bookings")
            .contentType(APPLICATION_JSON)
            .content(booking(typeId, iso(LocalDate.of(2026, 3, 11), 10, 0))));

    MvcResult result = mvc.perform(get("/api/bookings")).andReturn();

    openApi.assertConforms("Api_listBookings", result);
    assertThat(om.readTree(result.getResponse().getContentAsString())).hasSize(1);
  }

  @Test
  void validatorItselfRejectsPayloadsThatViolateTheContract() {
    // Доказывает, что валидатор не «пропускает всё»: из ответа удалён обязательный код ошибки.
    assertThatThrownBy(
            () -> openApi.assertConforms("Api_createBooking", 400, "{\"message\":\"x\"}"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Api_createBooking");
    assertThatThrownBy(
            () ->
                openApi.assertConforms(
                    "Api_createBooking", 400, "{\"code\":\"UNKNOWN\",\"message\":\"x\"}"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Api_createBooking");
  }

  private String idOf(MvcResult result) throws Exception {
    return om.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String meetingType(String name, int durationMinutes) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("durationMinutes", durationMinutes);
    return om.writeValueAsString(payload);
  }

  private String booking(String typeId, String start) throws Exception {
    return booking(typeId, start, "guest@example.com", "Гость");
  }

  private String booking(String typeId, String start, String email, String guestName)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("meetingTypeId", typeId);
    payload.put("start", start);
    payload.put("guestName", guestName);
    payload.put("guestEmail", email);
    return om.writeValueAsString(payload);
  }

  private static String iso(LocalDate date, int hour, int minute) {
    return date.atTime(LocalTime.of(hour, minute))
        .atZone(FixedClockConfig.OWNER_ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
