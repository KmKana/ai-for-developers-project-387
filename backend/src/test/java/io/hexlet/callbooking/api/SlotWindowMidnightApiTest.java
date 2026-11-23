package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import io.hexlet.callbooking.core.FixedClockNextDayConfig;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Окно пересчитывается по фиксированным часам: 11 марта даёт 12–25 марта. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockNextDayConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SlotWindowMidnightApiTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void windowShiftsByOneDayAfterMidnight() throws Exception {
    String body =
        om.writeValueAsString(
            Map.of("name", "Обзор", "description", "Короткий созвон", "durationMinutes", 30));
    String created =
        mvc.perform(post("/api/meeting-types").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String typeId = om.readTree(created).get("id").asText();

    String response =
        mvc.perform(get("/api/slots").queryParam("meetingTypeId", typeId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode slots = om.readTree(response);

    assertThat(slots).isNotEmpty();
    TreeSet<LocalDate> days = new TreeSet<>();
    for (JsonNode slot : slots) {
      days.add(
          OffsetDateTime.parse(slot.get("start").asText())
              .atZoneSameInstant(FixedClockConfig.OWNER_ZONE)
              .toLocalDate());
    }
    assertThat(days.first()).isEqualTo(LocalDate.of(2026, 3, 12));
    assertThat(days.last()).isEqualTo(LocalDate.of(2026, 3, 25));
    assertThat(days).doesNotContain(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 26));
  }
}
