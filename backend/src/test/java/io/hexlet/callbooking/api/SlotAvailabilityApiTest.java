package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Свободные Слоты API при фиксированном времени 10 марта: окно завтра + 13 дней. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SlotAvailabilityApiTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void windowStartsTomorrowAndEndsAfterFourteenDays() throws Exception {
    String typeId = createType(30);

    JsonNode slots = slots(typeId);

    assertThat(slots).isNotEmpty();
    TreeSet<LocalDate> days = new TreeSet<>();
    for (JsonNode slot : slots) {
      OffsetDateTime start = OffsetDateTime.parse(slot.get("start").asText());
      OffsetDateTime end = OffsetDateTime.parse(slot.get("end").asText());
      LocalDate day = start.atZoneSameInstant(FixedClockConfig.OWNER_ZONE).toLocalDate();
      assertThat(day).isBetween(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 24));
      assertThat(end.toInstant()).isEqualTo(start.toInstant().plusSeconds(30 * 60L));
      days.add(day);
    }
    assertThat(days.first()).isEqualTo(LocalDate.of(2026, 3, 11));
    assertThat(days.last()).isEqualTo(LocalDate.of(2026, 3, 24));
    assertThat(days).doesNotContain(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 25));
  }

  @Test
  void startsAreOnThirtyMinuteGridAndWholeSlotFitsWorkingHours() throws Exception {
    String halfHourType = createType(30);
    JsonNode halfHourSlots = slots(halfHourType);

    for (JsonNode slot : halfHourSlots) {
      ZonedDateTime start =
          OffsetDateTime.parse(slot.get("start").asText())
              .atZoneSameInstant(FixedClockConfig.OWNER_ZONE);
      ZonedDateTime end =
          OffsetDateTime.parse(slot.get("end").asText())
              .atZoneSameInstant(FixedClockConfig.OWNER_ZONE);
      assertThat(start.getSecond()).isZero();
      assertThat(start.getNano()).isZero();
      assertThat(start.getHour()).isBetween(9, 17);
      assertThat(start.getMinute() % 30).isZero();
      assertThat(end.toLocalTime()).isBeforeOrEqualTo(LocalTime.of(18, 0));
    }
    assertThat(latestStart(halfHourSlots)).isEqualTo(LocalTime.of(17, 30));

    String threeQuarterHourType = createType(45);
    JsonNode threeQuarterHourSlots = slots(threeQuarterHourType);
    assertThat(latestStart(threeQuarterHourSlots)).isEqualTo(LocalTime.of(17, 0));
  }

  @Test
  void weekendDaysAreInsideWindowButHaveNoSlots() throws Exception {
    String typeId = createType(30);

    TreeSet<LocalDate> days = new TreeSet<>();
    for (JsonNode slot : slots(typeId)) {
      days.add(
          OffsetDateTime.parse(slot.get("start").asText())
              .atZoneSameInstant(FixedClockConfig.OWNER_ZONE)
              .toLocalDate());
    }

    assertThat(days).contains(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 24));
    assertThat(days)
        .doesNotContain(
            LocalDate.of(2026, 3, 14),
            LocalDate.of(2026, 3, 15),
            LocalDate.of(2026, 3, 21),
            LocalDate.of(2026, 3, 22));
  }

  @Test
  void unknownMeetingTypeIsRejected() throws Exception {
    mvc.perform(get("/api/slots").queryParam("meetingTypeId", "no-such-type"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEETING_TYPE_NOT_FOUND"));
  }

  private static LocalTime latestStart(JsonNode slots) {
    LocalTime latest = LocalTime.MIN;
    for (JsonNode slot : slots) {
      LocalTime time =
          OffsetDateTime.parse(slot.get("start").asText())
              .atZoneSameInstant(FixedClockConfig.OWNER_ZONE)
              .toLocalTime();
      if (time.isAfter(latest)) {
        latest = time;
      }
    }
    return latest;
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
}
