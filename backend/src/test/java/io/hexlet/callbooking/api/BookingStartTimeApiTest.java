package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Начало встречи в любом корректном смещении приводится к зоне владельца до проверки правил. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingStartTimeApiTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void rfc3339WithForeignOffsetIsNormalizedToOwnerZone() throws Exception {
    String typeId = createType();

    String response =
        mvc.perform(
                post("/api/bookings")
                    .contentType(APPLICATION_JSON)
                    .content(booking(typeId, "2026-03-11T05:00:00Z")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.start").value("2026-03-11T10:00:00+05:00"))
            .andExpect(jsonPath("$.end").value("2026-03-11T10:30:00+05:00"))
            .andExpect(jsonPath("$.createdAt").value("2026-03-10T11:00:00+05:00"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(om.readTree(response).get("meetingTypeId").asText()).isEqualTo(typeId);
    String upcoming =
        mvc.perform(get("/api/bookings"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(om.readTree(upcoming)).hasSize(1);
  }

  @Test
  void invalidOrAmbiguousStartIsRejected() throws Exception {
    String typeId = createType();

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, "2026-03-11T10:00:00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/bookings")
                .contentType(APPLICATION_JSON)
                .content(booking(typeId, "2026-02-30T10:00:00+05:00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(
            post("/api/bookings").contentType(APPLICATION_JSON).content(booking(typeId, "не-дата")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private String createType() throws Exception {
    return ApiTestFixtures.createType(mvc, om, "Обзор", "Короткий созвон", 30);
  }

  private static String booking(String typeId, String start) {
    return ApiTestFixtures.bookingJson(typeId, start, "Гость", "guest@example.com");
  }
}
