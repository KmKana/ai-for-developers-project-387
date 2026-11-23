package io.hexlet.callbooking.core;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Фиксированные часы для тестов календарных правил в зоне владельца. */
@TestConfiguration(proxyBeanMethods = false)
public final class FixedClockConfig {

  /** Часовой пояс владельца; константа теста не зависит от реализации. */
  public static final ZoneId OWNER_ZONE = ZoneId.of("Asia/Almaty");

  /** 10 марта 2026 года: окно должно быть 11–24 марта. */
  public static final Clock MARCH_10 =
      Clock.fixed(Instant.parse("2026-03-10T06:00:00Z"), OWNER_ZONE);

  /** 11 марта 2026 года: после полуночи окно должно быть 12–25 марта. */
  public static final Clock MARCH_11 =
      Clock.fixed(Instant.parse("2026-03-11T06:00:00Z"), OWNER_ZONE);

  @Bean
  @Primary
  Clock march10Clock() {
    return MARCH_10;
  }
}
