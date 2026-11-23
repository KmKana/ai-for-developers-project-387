package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Создание Записи Гостём через HTTP-шов contract API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingCreationApiTest {

  /** 10:00 по времени владельца, среда, первый день окна при часах 10 марта. */
  private static final String START = "2026-03-11T05:00:00Z";

  /** Часы теста зафиксированы на 10 марта 2026, 11:00 по времени владельца. */
  private static final String EXPECTED_CREATED_AT = "2026-03-10T11:00:00+05:00";

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void createsBookingWithCompleteServerGeneratedBody() throws Exception {
    String typeId = createType("Знакомство", 30);

    String response =
        mvc.perform(
                post("/api/bookings")
                    .contentType(APPLICATION_JSON)
                    .content(booking(typeId, START, "Алексей", "alex@example.com").toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.meetingTypeId").value(typeId))
            .andExpect(jsonPath("$.meetingTypeName").value("Знакомство"))
            .andExpect(jsonPath("$.start").value("2026-03-11T10:00:00+05:00"))
            .andExpect(jsonPath("$.end").value("2026-03-11T10:30:00+05:00"))
            .andExpect(jsonPath("$.guestName").value("Алексей"))
            .andExpect(jsonPath("$.guestEmail").value("alex@example.com"))
            .andExpect(jsonPath("$.createdAt").value(EXPECTED_CREATED_AT))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var body = om.readTree(response);
    assertThatUuid(body.get("id").asText());
    assertThat(body.size()).isEqualTo(8);
  }

  @Test
  void endIsComputedFromTypeDurationAndForeignOffset() throws Exception {
    String typeId = createType("Разбор", 45);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    booking(typeId, "2026-03-11T04:00:00Z", "Гость", "guest@example.com")
                        .toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.start").value("2026-03-11T09:00:00+05:00"))
        .andExpect(jsonPath("$.end").value("2026-03-11T09:45:00+05:00"));
  }

  @Test
  void clientProvidedEndIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);
    ObjectNode body = booking(typeId, START, "Гость", "guest@example.com");
    body.put("end", "2026-03-11T05:30:00Z");

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("end")));

    expectNoBookings();
  }

  @Test
  void meetingTypeNameSnapshotComesFromCatalogNotFromRequest() throws Exception {
    String typeId = createType("Официальное название", 30);
    ObjectNode body = booking(typeId, START, "Гость", "guest@example.com");
    body.put("meetingTypeName", "Подмена из запроса");

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.meetingTypeName").value("Официальное название"));

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].meetingTypeName").value("Официальное название"));
  }

  @Test
  void idAndCreatedAtAreGeneratedByServer() throws Exception {
    String typeId = createType("Знакомство", 30);
    ObjectNode body = booking(typeId, START, "Гость", "guest@example.com");
    body.put("id", "client-supplied-id");
    body.put("createdAt", "2020-01-01T00:00:00Z");

    String response =
        mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").value(EXPECTED_CREATED_AT))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(om.readTree(response).get("id").asText()).isNotEqualTo("client-supplied-id");
  }

  @Test
  void emptyGuestNameIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, START, "", "guest@example.com").toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("Имя")));

    expectNoBookings();
  }

  @Test
  void whitespaceGuestNameIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, START, "   ", "guest@example.com").toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    expectNoBookings();
  }

  @Test
  void invalidGuestEmailIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, START, "Гость", "не-email").toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message", containsString("email")));

    expectNoBookings();
  }

  @Test
  void missingGuestEmailIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);
    ObjectNode body = booking(typeId, START, "Гость", "guest@example.com");
    body.remove("guestEmail");

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    expectNoBookings();
  }

  @Test
  void unknownMeetingTypeIdIsRejected() throws Exception {
    createType("Знакомство", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking("unknown-type", START, "Гость", "guest@example.com").toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEETING_TYPE_NOT_FOUND"))
        .andExpect(jsonPath("$.message", containsString("unknown-type")));

    expectNoBookings();
  }

  @Test
  void missingMeetingTypeIdIsRejected() throws Exception {
    createType("Знакомство", 30);
    ObjectNode body = booking("type-id", START, "Гость", "guest@example.com");
    body.remove("meetingTypeId");

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    expectNoBookings();
  }

  @Test
  void missingStartIsRejected() throws Exception {
    String typeId = createType("Знакомство", 30);
    ObjectNode body = booking(typeId, START, "Гость", "guest@example.com");
    body.remove("start");

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    expectNoBookings();
  }

  @Test
  void unavailableTimeReturnsConflictWithCodeAndMessage() throws Exception {
    String typeId = createType("Знакомство", 30);
    String body = booking(typeId, START, "Гость", "guest@example.com").toString();

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"))
        .andExpect(jsonPath("$.message").value("Выбранное время уже занято. Выберите другой слот"));

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void offGridStartReturnsConflictWithMessage() throws Exception {
    String typeId = createType("Знакомство", 30);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(
                    booking(typeId, "2026-03-11T04:10:00Z", "Гость", "guest@example.com")
                        .toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OFF_GRID"))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  private void expectNoBookings() throws Exception {
    mvc.perform(get("/api/bookings")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  }

  private static void assertThatUuid(String value) {
    assertThat(UUID.fromString(value)).isNotNull();
  }

  private String createType(String name, int durationMinutes) throws Exception {
    ObjectNode body = om.createObjectNode();
    body.put("name", name);
    body.put("description", "Короткий созвон");
    body.put("durationMinutes", durationMinutes);
    String response =
        mvc.perform(
                post("/api/meeting-types").contentType(APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  private ObjectNode booking(String typeId, String start, String guestName, String guestEmail) {
    ObjectNode body = om.createObjectNode();
    body.put("meetingTypeId", typeId);
    body.put("start", start);
    body.put("guestName", guestName);
    body.put("guestEmail", guestEmail);
    return body;
  }
}
