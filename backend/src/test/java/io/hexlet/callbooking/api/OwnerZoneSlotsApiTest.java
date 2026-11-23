package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.MutableUtcClockConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Правила считаются в Asia/Almaty, даже если внедрённые часы имеют другую зону. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableUtcClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OwnerZoneSlotsApiTest {

  private static final ZoneId OWNER_ZONE = ZoneId.of("Asia/Almaty");

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void slotsUseOwnerZoneAndWindowRecomputesAtOwnerMidnight() throws Exception {
    // 2026-03-09T19:00Z — это уже 10 марта 00:00 в Asia/Almaty.
    MutableUtcClockConfig.setNow("2026-03-09T19:00:00Z");
    String typeId = createType(30);

    JsonNode beforeMidnight = slots(typeId);
    TreeSet<LocalDate> firstWindow = days(beforeMidnight);
    assertThat(firstWindow.first()).isEqualTo(LocalDate.of(2026, 3, 11));
    assertThat(firstWindow.last()).isEqualTo(LocalDate.of(2026, 3, 24));
    assertThat(start(beforeMidnight.get(0)).toString()).endsWith("+05:00");
    assertThat(start(beforeMidnight.get(0)).atZoneSameInstant(OWNER_ZONE).toLocalDateTime())
        .isEqualTo(LocalDateTime.of(2026, 3, 11, 9, 0));

    // Одна секунда до полуночи владельца: окно ещё старое.
    MutableUtcClockConfig.setNow("2026-03-10T18:59:59Z");
    assertThat(days(slots(typeId)).first()).isEqualTo(LocalDate.of(2026, 3, 11));

    // Ровно полночь владельца: окно сдвинулось на один день.
    MutableUtcClockConfig.setNow("2026-03-10T19:00:00Z");
    TreeSet<LocalDate> nextWindow = days(slots(typeId));
    assertThat(nextWindow.first()).isEqualTo(LocalDate.of(2026, 3, 12));
    assertThat(nextWindow.last()).isEqualTo(LocalDate.of(2026, 3, 25));
  }

  @Test
  void bookingTimesAreNormalizedToOwnerZoneWithForeignClockZone() throws Exception {
    MutableUtcClockConfig.setNow("2026-03-09T19:00:00Z");
    String typeId = createType(45);

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, "2026-03-11T05:00:00Z")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.start").value("2026-03-11T10:00:00+05:00"))
        .andExpect(jsonPath("$.end").value("2026-03-11T10:45:00+05:00"))
        .andExpect(jsonPath("$.createdAt").value("2026-03-10T00:00:00+05:00"));
  }

  @Test
  void missingMeetingTypeIdIsValidationError() throws Exception {
    mvc.perform(get("/api/slots"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private String createType(int durationMinutes) throws Exception {
    String body =
        om.writeValueAsString(
            Map.of(
                "name", "Обзор",
                "description", "Короткий созвон",
                "durationMinutes", durationMinutes));
    String response =
        mvc.perform(post("/api/meeting-types").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  private JsonNode slots(String typeId) throws Exception {
    String response =
        mvc.perform(get("/api/slots").queryParam("meetingTypeId", typeId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response);
  }

  private static TreeSet<LocalDate> days(JsonNode slots) {
    TreeSet<LocalDate> days = new TreeSet<>();
    for (JsonNode slot : slots) {
      days.add(start(slot).atZoneSameInstant(OWNER_ZONE).toLocalDate());
    }
    return days;
  }

  private static OffsetDateTime start(JsonNode slot) {
    return OffsetDateTime.parse(slot.get("start").asText());
  }

  private static String booking(String typeId, String start) {
    return """
        {"meetingTypeId":"%s","start":"%s",
         "guestName":"Гость","guestEmail":"guest@example.com"}
        """
        .formatted(typeId, start);
  }
}
