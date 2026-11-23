package io.hexlet.callbooking.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Создание типа встречи: границы контракта и единый формат ошибки VALIDATION_ERROR. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MeetingTypeValidationApiTest {

  private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  private static final String NAME_REQUIRED = "Название типа встречи обязательно";
  private static final String NAME_TOO_LONG = "Название не длиннее 100 символов";
  private static final String DESCRIPTION_TOO_LONG = "Описание не длиннее 500 символов";
  private static final String DURATION_OUT_OF_RANGE = "Длительность должна быть от 5 до 480 минут";
  private static final String MALFORMED_BODY =
      "Некорректное тело запроса: проверьте формат JSON и дат";

  @Autowired private MockMvc mvc;

  @Test
  void acceptsNameOfOneCharacter() throws Exception {
    createType("А", null, 30);
  }

  @Test
  void acceptsNameOfOneHundredCharacters() throws Exception {
    String name = "Н".repeat(100);

    String id = createType(name, null, 30);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].name").value(name));
  }

  @Test
  void acceptsDescriptionOfFiveHundredCharacters() throws Exception {
    String description = "О".repeat(500);

    String id = createType("Знакомство", description, 30);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].description").value(description));
  }

  @Test
  void acceptsMinimumDuration() throws Exception {
    String id = createType("Короткая встреча", null, 5);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].durationMinutes").value(5));
  }

  @Test
  void acceptsMaximumDuration() throws Exception {
    String id = createType("Длинная встреча", null, 480);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].durationMinutes").value(480));
  }

  @Test
  void createdTypeIsImmediatelyVisibleInCatalog() throws Exception {
    String id = createType("Знакомство", "Короткий созвон", 30);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].name").value("Знакомство"))
        .andExpect(jsonPath("$[0].description").value("Короткий созвон"))
        .andExpect(jsonPath("$[0].durationMinutes").value(30));
  }

  @Test
  void rejectsEmptyName() throws Exception {
    expectValidationError(body("", null, 30), NAME_REQUIRED);
  }

  @Test
  void rejectsNameLongerThanOneHundredCharacters() throws Exception {
    expectValidationError(body("Н".repeat(101), null, 30), NAME_TOO_LONG);
  }

  @Test
  void rejectsDescriptionLongerThanFiveHundredCharacters() throws Exception {
    expectValidationError(body("Знакомство", "О".repeat(501), 30), DESCRIPTION_TOO_LONG);
  }

  @Test
  void rejectsDurationBelowMinimum() throws Exception {
    expectValidationError(body("Знакомство", null, 4), DURATION_OUT_OF_RANGE);
  }

  @Test
  void rejectsDurationAboveMaximum() throws Exception {
    expectValidationError(body("Знакомство", null, 481), DURATION_OUT_OF_RANGE);
  }

  @Test
  void rejectsFractionalDuration() throws Exception {
    expectValidationError("{\"name\":\"Знакомство\",\"durationMinutes\":30.5}", MALFORMED_BODY);
  }

  private String createType(String name, String description, int durationMinutes) throws Exception {
    String response =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(body(name, description, durationMinutes)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.durationMinutes").value(durationMinutes))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new ObjectMapper().readTree(response).get("id").asText();
  }

  private void expectValidationError(String requestBody, String expectedMessage) throws Exception {
    mvc.perform(post("/api/meeting-types").contentType(APPLICATION_JSON).content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(VALIDATION_ERROR))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  private String body(String name, String description, int durationMinutes) throws Exception {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("name", name);
    if (description != null) {
      fields.put("description", description);
    }
    fields.put("durationMinutes", durationMinutes);
    return new ObjectMapper().writeValueAsString(fields);
  }
}
