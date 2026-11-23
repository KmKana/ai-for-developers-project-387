package io.hexlet.callbooking.api.dto;

import java.time.OffsetDateTime;

/** Запись гостя на встречу. */
public record BookingDto(
    String id,
    String meetingTypeId,
    String meetingTypeName,
    OffsetDateTime start,
    OffsetDateTime end,
    String guestName,
    String guestEmail,
    OffsetDateTime createdAt) {}
