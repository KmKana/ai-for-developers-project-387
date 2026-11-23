package io.hexlet.callbooking.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hexlet.callbooking.api.dto.MeetingTypeInputDto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Правило «одно и то же время нельзя занять дважды, даже одновременными запросами» проверяется на
 * HTTP-шве: выигрывает ровно один запрос, остальные получают {@code SLOT_CONFLICT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingConcurrencyTest {

  private static final String SLOT_CONFLICT = "SLOT_CONFLICT";
  private static final String SLOT_CONFLICT_MESSAGE =
      "Выбранное время уже занято. Выберите другой слот";

  /** Первый день окна при фиксированном времени #15: 10 марта 2026 года, 12:00 в Алматы. */
  private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 11);

  @Autowired private TestRestTemplate rest;

  @Autowired private MeetingTypeService meetingTypeService;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void onlyOneOfTwelveConcurrentHttpRequestsWinsTheSlot() throws Exception {
    String typeId = createHalfHourType();

    assertSingleWinner(typeId, startAt(12, 0), 12);
  }

  @Test
  void onlyOneOfThirtyTwoConcurrentHttpRequestsWinsTheSlot() throws Exception {
    String typeId = createHalfHourType();

    assertSingleWinner(typeId, startAt(12, 0), 32);
  }

  /** Все запросы идут через HTTP; проверяются и ответы, и итоговый список Владельца. */
  private void assertSingleWinner(String typeId, String start, int requests) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(requests);
    try {
      List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
      for (int i = 0; i < requests; i++) {
        int guestNo = i;
        tasks.add(() -> postBooking(typeId, start, guestNo));
      }
      List<Future<ResponseEntity<String>>> results = pool.invokeAll(tasks);

      int created = 0;
      int conflicts = 0;
      for (Future<ResponseEntity<String>> result : results) {
        ResponseEntity<String> response = result.get(30, TimeUnit.SECONDS);
        if (response.getStatusCode().value() == 201) {
          created++;
          JsonNode booking = objectMapper.readTree(response.getBody());
          assertThat(booking.get("id").asText()).isNotBlank();
          assertThat(booking.get("start").asText()).isEqualTo(start);
        } else {
          assertThat(response.getStatusCode().value()).isEqualTo(409);
          JsonNode error = objectMapper.readTree(response.getBody());
          assertThat(error.get("code").asText()).isEqualTo(SLOT_CONFLICT);
          assertThat(error.get("message").asText()).isEqualTo(SLOT_CONFLICT_MESSAGE);
          conflicts++;
        }
      }

      assertThat(created).isEqualTo(1);
      assertThat(conflicts).isEqualTo(requests - 1);

      List<Map<String, Object>> bookings =
          rest.exchange(
                  "/api/bookings",
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<Map<String, Object>>>() {})
              .getBody();
      assertThat(bookings).hasSize(1);
      assertThat(bookings.get(0).get("start")).isEqualTo(start);
    } finally {
      pool.shutdownNow();
    }
  }

  private ResponseEntity<String> postBooking(String typeId, String start, int guestNo) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body =
        """
                {"meetingTypeId":"%s","start":"%s",
                 "guestName":"Guest %d","guestEmail":"guest%d@example.com"}
                """
            .formatted(typeId, start, guestNo, guestNo);
    return rest.postForEntity("/api/bookings", new HttpEntity<>(body, headers), String.class);
  }

  private String createHalfHourType() {
    return meetingTypeService.create(new MeetingTypeInputDto("Знакомство", "30 минут", 30)).id();
  }

  private static String startAt(int hour, int minute) {
    return FIRST_DAY
        .atTime(LocalTime.of(hour, minute))
        .atZone(FixedClockConfig.OWNER_ZONE)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
