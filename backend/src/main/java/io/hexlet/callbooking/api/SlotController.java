package io.hexlet.callbooking.api;

import io.hexlet.callbooking.api.dto.SlotDto;
import io.hexlet.callbooking.core.SlotService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Свободные слоты выбранного типа встречи на ближайшие 14 дней. */
@RestController
@RequestMapping("/api")
public class SlotController {

  private final SlotService service;

  public SlotController(SlotService service) {
    this.service = service;
  }

  @GetMapping("/slots")
  public List<SlotDto> slots(@RequestParam String meetingTypeId) {
    return service.availableSlots(meetingTypeId);
  }
}
