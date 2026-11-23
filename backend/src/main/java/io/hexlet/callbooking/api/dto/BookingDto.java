package io.hexlet.callbooking.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** Запись гостя на встречу. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingDto(
    String id,
    String meetingTypeId,
    String meetingTypeName,
    OffsetDateTime start,
    OffsetDateTime end,
    String guestName,
    String guestEmail,
    String topic,
    OffsetDateTime createdAt) {}
