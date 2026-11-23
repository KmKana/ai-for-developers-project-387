package io.hexlet.callbooking.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Каталог типов встреч: ответ должен совпадать со структурой контракта. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MeetingTypeCatalogApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void catalogReturnsMeetingTypesWithContractFields() throws Exception {
    createType(
        "{\"name\":\"Знакомство\",\"description\":\"Короткий созвон\",\"durationMinutes\":30}");

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").isString())
        .andExpect(jsonPath("$[0].id").isNotEmpty())
        .andExpect(jsonPath("$[0].name").value("Знакомство"))
        .andExpect(jsonPath("$[0].description").value("Короткий созвон"))
        .andExpect(jsonPath("$[0].durationMinutes").value(30));
  }

  @Test
  void catalogOmitsDescriptionWhenTypeHasNone() throws Exception {
    createType("{\"name\":\"Без описания\",\"durationMinutes\":45}");

    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").isString())
        .andExpect(jsonPath("$[0].id").isNotEmpty())
        .andExpect(jsonPath("$[0].name").value("Без описания"))
        .andExpect(jsonPath("$[0]").value(not(hasKey("description"))))
        .andExpect(jsonPath("$[0].durationMinutes").value(45));
  }

  private void createType(String body) throws Exception {
    mvc.perform(post("/api/meeting-types").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }
}
