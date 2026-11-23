package io.hexlet.callbooking.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Клиентские маршруты React. Собранный Vite-бандл лежит в classpath:/static, а эти адреса не должны
 * попадать в REST API.
 */
@Controller
public class SpaForwardingController {

  @GetMapping({"/", "/catalog", "/booking", "/book/{id}", "/event-types", "/bookings"})
  public String forwardToIndex() {
    return "forward:/index.html";
  }
}
