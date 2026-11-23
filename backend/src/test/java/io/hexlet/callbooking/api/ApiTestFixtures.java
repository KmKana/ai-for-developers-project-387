package io.hexlet.callbooking.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.core.FixedClockConfig;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Общие фикстуры HTTP-тестов API: только подготовка запросов, без проверки бизнес-правил. */
public final class ApiTestFixtures {

  private ApiTestFixtures() {}

  public static String createType(
      MockMvc mvc, ObjectMapper om, String name, String description, int durationMinutes)
      throws Exception {
    String response =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(meetingTypeJson(om, name, description, durationMinutes)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  public static String createType(MockMvc mvc, ObjectMapper om, String name, int durationMinutes)
      throws Exception {
    return createType(mvc, om, name, "Описание", durationMinutes);
  }

  public static String meetingTypeJson(ObjectMapper om, String name, int durationMinutes)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("durationMinutes", durationMinutes);
    return om.writeValueAsString(payload);
  }

  public static String bookingJson(
      String typeId, String start, String guestName, String guestEmail) {
    return """
        {"meetingTypeId":"%s","start":"%s",
         "guestName":"%s","guestEmail":"%s"}
        """
        .formatted(typeId, start, guestName, guestEmail);
  }

  public static ResultActions book(
      MockMvc mvc, String typeId, String start, String guestName, String guestEmail)
      throws Exception {
    return mvc.perform(
        post("/api/bookings")
            .contentType(APPLICATION_JSON)
            .content(bookingJson(typeId, start, guestName, guestEmail)));
  }

  public static String iso(String date, int hour, int minute) {
    return LocalDate.parse(date)
        .atTime(LocalTime.of(hour, minute))
        .atZone(FixedClockConfig.OWNER_ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private static String meetingTypeJson(
      ObjectMapper om, String name, String description, int durationMinutes) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("description", description);
    payload.put("durationMinutes", durationMinutes);
    return om.writeValueAsString(payload);
  }
}
