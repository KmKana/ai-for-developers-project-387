package io.hexlet.callbooking.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Список предстоящих Записей Владельца через HTTP-шов контракта с управляемым временем. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OwnerBookingsApiTest.OwnerClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OwnerBookingsApiTest {

  private static final ZoneId OWNER_ZONE = ZoneId.of("Asia/Almaty");

  /** Момент из фиксированных часов: 10 марта 2026, 11:00 по времени Владельца. */
  private static final Instant MARCH_10_1100 = Instant.parse("2026-03-10T06:00:00Z");

  private static final String CREATED_AT = "2026-03-10T11:00:00+05:00";

  private static final AtomicReference<Instant> NOW = new AtomicReference<>(MARCH_10_1100);

  /** Управляемые часы Владельца: позволяют проверить исключение прошедших Записей. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OwnerClockConfig {

    @Bean
    @Primary
    Clock ownerClock() {
      return new Clock() {
        @Override
        public ZoneId getZone() {
          return OWNER_ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return Clock.fixed(NOW.get(), zone);
        }

        @Override
        public Instant instant() {
          return NOW.get();
        }
      };
    }
  }

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper om;

  @BeforeEach
  void fixClock() {
    NOW.set(MARCH_10_1100);
  }

  @Test
  void newBookingAppearsImmediatelyInOwnerListWithFullSnapshot() throws Exception {
    String typeId = createType("Знакомство", 30);
    String created = createBooking(typeId, "2026-03-11T05:00:00Z", "Алексей", "alex@example.com");
    String bookingId = om.readTree(created).get("id").asText();

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(bookingId))
        .andExpect(jsonPath("$[0].meetingTypeId").value(typeId))
        .andExpect(jsonPath("$[0].meetingTypeName").value("Знакомство"))
        .andExpect(jsonPath("$[0].start").value("2026-03-11T10:00:00+05:00"))
        .andExpect(jsonPath("$[0].end").value("2026-03-11T10:30:00+05:00"))
        .andExpect(jsonPath("$[0].guestName").value("Алексей"))
        .andExpect(jsonPath("$[0].guestEmail").value("alex@example.com"))
        .andExpect(jsonPath("$[0].createdAt").value(CREATED_AT));
  }

  @Test
  void bookingsOfSeveralTypesAreSortedByStart() throws Exception {
    String introId = createType("Знакомство", 30);
    String deepDiveId = createType("Разбор", 45);

    // Создаём записи не в хронологическом порядке.
    createBooking(introId, "2026-03-11T10:00:00Z", "Третий", "third@example.com");
    createBooking(deepDiveId, "2026-03-11T04:30:00Z", "Первый", "first@example.com");
    createBooking(introId, "2026-03-11T07:00:00Z", "Второй", "second@example.com");

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].start").value("2026-03-11T09:30:00+05:00"))
        .andExpect(jsonPath("$[0].meetingTypeName").value("Разбор"))
        .andExpect(jsonPath("$[0].guestName").value("Первый"))
        .andExpect(jsonPath("$[0].end").value("2026-03-11T10:15:00+05:00"))
        .andExpect(jsonPath("$[1].start").value("2026-03-11T12:00:00+05:00"))
        .andExpect(jsonPath("$[1].meetingTypeName").value("Знакомство"))
        .andExpect(jsonPath("$[1].guestName").value("Второй"))
        .andExpect(jsonPath("$[2].start").value("2026-03-11T15:00:00+05:00"))
        .andExpect(jsonPath("$[2].meetingTypeName").value("Знакомство"))
        .andExpect(jsonPath("$[2].guestName").value("Третий"));
  }

  @Test
  void pastBookingsAreExcludedByInjectedClock() throws Exception {
    String typeId = createType("Знакомство", 30);
    createBooking(typeId, "2026-03-11T05:00:00Z", "Прошлая", "past@example.com");
    createBooking(typeId, "2026-03-12T05:00:00Z", "Будущая", "future@example.com");

    // 11 марта, 12:00 по времени Владельца: утренняя Запись уже началась.
    NOW.set(Instant.parse("2026-03-11T07:00:00Z"));

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].start").value("2026-03-12T10:00:00+05:00"))
        .andExpect(jsonPath("$[0].guestName").value("Будущая"));
  }

  @Test
  void snapshotNameIsNotTakenFromBookingRequest() throws Exception {
    String typeId = createType("Официальное название", 30);

    var body =
        om.createObjectNode()
            .put("meetingTypeId", typeId)
            .put("start", "2026-03-11T05:00:00Z")
            .put("meetingTypeName", "Подмена из запроса")
            .put("guestName", "Гость")
            .put("guestEmail", "guest@example.com");
    mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/bookings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].meetingTypeName").value("Официальное название"));
  }

  private String createType(String name, int durationMinutes) throws Exception {
    String response =
        mvc.perform(
                post("/api/meeting-types")
                    .contentType(APPLICATION_JSON)
                    .content(
                        om.writeValueAsString(
                            Map.of(
                                "name",
                                name,
                                "description",
                                "Описание типа встречи",
                                "durationMinutes",
                                durationMinutes))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return om.readTree(response).get("id").asText();
  }

  private String createBooking(String typeId, String start, String guestName, String guestEmail)
      throws Exception {
    var body =
        om.createObjectNode()
            .put("meetingTypeId", typeId)
            .put("start", start)
            .put("guestName", guestName)
            .put("guestEmail", guestEmail);
    return mvc.perform(post("/api/bookings").contentType(APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
