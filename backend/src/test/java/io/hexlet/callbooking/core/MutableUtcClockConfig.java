package io.hexlet.callbooking.core;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Часы в чужой зоне (UTC) с управляемым моментом: проверка нормализации к Asia/Almaty. */
@TestConfiguration(proxyBeanMethods = false)
public final class MutableUtcClockConfig {

  private static final AtomicReference<Instant> NOW = new AtomicReference<>();

  /** Задать текущий момент в UTC; зона часов остаётся чужой по отношению к владельцу. */
  public static void setNow(String instant) {
    NOW.set(Instant.parse(instant));
  }

  @Bean
  @Primary
  Clock utcClock() {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneId.of("UTC");
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
