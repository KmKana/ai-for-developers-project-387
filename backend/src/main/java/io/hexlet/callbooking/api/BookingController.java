package io.hexlet.callbooking.api;

import io.hexlet.callbooking.api.dto.BookingDto;
import io.hexlet.callbooking.api.dto.BookingInputDto;
import io.hexlet.callbooking.core.BookingService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Записи на встречи: создание (гость) и список предстоящих (владелец). */
@RestController
@RequestMapping("/api")
public class BookingController {

  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  @PostMapping("/bookings")
  public ResponseEntity<BookingDto> create(@RequestBody BookingInputDto input) {
    BookingDto created = service.book(input);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/bookings")
  public List<BookingDto> list() {
    return service.upcoming();
  }
}
