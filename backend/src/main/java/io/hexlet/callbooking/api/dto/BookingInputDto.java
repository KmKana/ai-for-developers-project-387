package io.hexlet.callbooking.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.hexlet.callbooking.core.ApiErrorCode;
import io.hexlet.callbooking.core.ApiRequestException;
import java.time.OffsetDateTime;

/** Данные для записи на слот. */
public record BookingInputDto(
    String meetingTypeId, OffsetDateTime start, String guestName, String guestEmail) {

  /**
   * Клиент передаёт только начало: окончание всегда вычисляется сервером по длительности Типа
   * встречи. Поле описано в контракте только в ответе, поэтому его появление в запросе — ошибка.
   */
  @JsonSetter("end")
  public void rejectEnd(Object end) {
    if (end != null) {
      throw new ApiRequestException(
          400,
          ApiErrorCode.VALIDATION_ERROR,
          "Клиент не должен передавать end: окончание вычисляет сервер");
    }
  }
}
