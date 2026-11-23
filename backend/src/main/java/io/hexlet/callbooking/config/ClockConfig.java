package io.hexlet.callbooking.config;

import io.hexlet.callbooking.core.BookingRules;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Часы приложения: в продукте системное время в зоне владельца. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  Clock systemClock() {
    return Clock.system(BookingRules.ZONE);
  }
}
