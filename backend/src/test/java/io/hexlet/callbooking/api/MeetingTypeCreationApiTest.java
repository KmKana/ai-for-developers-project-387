package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;

/** Создание Типа встречи Владельцем через HTTP API и немедленное появление в каталоге. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MeetingTypeCreationApiTest {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @Test
  void ownerCreatesTypeAndImmediatelySeesItInCatalog() throws Exception {
    MvcResult created =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(body("Знакомство", "Короткий созвон", 30)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Знакомство"))
            .andExpect(jsonPath("$.description").value("Короткий созвон"))
            .andExpect(jsonPath("$.durationMinutes").value(30))
            .andReturn();

    String id = om.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].name").value("Знакомство"))
        .andExpect(jsonPath("$[0].description").value("Короткий созвон"))
        .andExpect(jsonPath("$[0].durationMinutes").value(30));
  }

  @Test
  void serverGeneratesOpaqueIdEvenWhenClientSuppliesOne() throws Exception {
    String first = createTypeWithClientId("client-chosen-id", "Первый тип");
    String second = createTypeWithClientId("client-chosen-id", "Второй тип");

    assertThat(first).isNotEqualTo("client-chosen-id");
    assertThat(second).isNotEqualTo("client-chosen-id");
    assertThat(second).isNotEqualTo(first);

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value(first))
        .andExpect(jsonPath("$[1].id").value(second));
  }

  @Test
  void nameAndDurationLowerBoundariesAreAccepted() throws Exception {
    mvc.perform(
            post("/api/meeting-types").contentType(APPLICATION_JSON).content(body("А", null, 5)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("А"))
        .andExpect(jsonPath("$.durationMinutes").value(5));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name").value("А"))
        .andExpect(jsonPath("$[0].durationMinutes").value(5));
  }

  @Test
  void nameDescriptionAndDurationUpperBoundariesAreAccepted() throws Exception {
    String name = "а".repeat(100);
    String description = "б".repeat(500);

    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body(name, description, 480)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value(name))
        .andExpect(jsonPath("$.description").value(description))
        .andExpect(jsonPath("$.durationMinutes").value(480));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name").value(name))
        .andExpect(jsonPath("$[0].description").value(description))
        .andExpect(jsonPath("$[0].durationMinutes").value(480));
  }

  @Test
  void emptyNameIsRejectedWithValidationError() throws Exception {
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types").contentType(APPLICATION_JSON).content(body("", null, 30))));
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("   ", null, 30))));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void nameLongerThanContractIsRejectedWithValidationError() throws Exception {
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("а".repeat(101), null, 30))));
    // Длина строки контракта считается до обрезки пробелов сервером.
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("а".repeat(100) + " ", null, 30))));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void descriptionLongerThanContractIsRejectedWithValidationError() throws Exception {
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("Знакомство", "б".repeat(501), 30))));
    // Длина строки контракта считается до обрезки пробелов сервером.
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("Знакомство", "б".repeat(500) + " ", 30))));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void durationOutsideContractIsRejectedWithValidationError() throws Exception {
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("Короткая встреча", null, 4))));
    assertValidationError(
        mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content(body("Длинная встреча", null, 481))));

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void nonIntegerDurationIsRejectedWithValidationError() throws Exception {
    mvc.perform(
            post("/api/meeting-types")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Знакомство\",\"durationMinutes\":30.5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.message").isNotEmpty());

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  private String createTypeWithClientId(String clientId, String name) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", clientId);
    payload.put("name", name);
    payload.put("durationMinutes", 30);

    MvcResult result =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(om.writeValueAsString(payload)))
            .andExpect(status().isCreated())
            .andReturn();
    return om.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String body(String name, String description, Integer durationMinutes) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (name != null) {
      payload.put("name", name);
    }
    if (description != null) {
      payload.put("description", description);
    }
    if (durationMinutes != null) {
      payload.put("durationMinutes", durationMinutes);
    }
    return om.writeValueAsString(payload);
  }

  private void assertValidationError(org.springframework.test.web.servlet.ResultActions actions)
      throws Exception {
    actions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.message").isNotEmpty());
  }
}
