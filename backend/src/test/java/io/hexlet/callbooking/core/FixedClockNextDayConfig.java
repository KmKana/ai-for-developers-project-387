package io.hexlet.callbooking.core;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Фиксированные часы следующего дня: проверка сдвига окна после полуночи. */
@TestConfiguration(proxyBeanMethods = false)
public final class FixedClockNextDayConfig {

  @Bean
  @Primary
  Clock march11Clock() {
    return FixedClockConfig.MARCH_11;
  }
}
