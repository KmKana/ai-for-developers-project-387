package io.hexlet.callbooking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Дымовой тест каркаса: приложение поднимается и отвечает 200 на запрос. */
@SpringBootTest
@AutoConfigureMockMvc
class SmokeTest {

  @Autowired private MockMvc mvc;

  @Test
  void appBootsAndAnswers() throws Exception {
    mvc.perform(get("/api/meeting-types"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
}
