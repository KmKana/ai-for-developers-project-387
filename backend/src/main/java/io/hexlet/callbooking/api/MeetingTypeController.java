package io.hexlet.callbooking.api;

import io.hexlet.callbooking.api.dto.MeetingTypeDto;
import io.hexlet.callbooking.api.dto.MeetingTypeInputDto;
import io.hexlet.callbooking.core.MeetingTypeService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Каталог типов встреч: просмотр (гость и владелец) и создание (владелец). */
@RestController
@RequestMapping("/api")
public class MeetingTypeController {

  private final MeetingTypeService service;

  public MeetingTypeController(MeetingTypeService service) {
    this.service = service;
  }

  @GetMapping("/meeting-types")
  public List<MeetingTypeDto> list() {
    return service.list();
  }

  @PostMapping("/meeting-types")
  public ResponseEntity<MeetingTypeDto> create(@RequestBody MeetingTypeInputDto input) {
    MeetingTypeDto created = service.create(input);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }
}
