package io.hexlet.callbooking.store;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Запись гостя на встречу (внутреннее представление). */
public record Booking(
    String id,
    String meetingTypeId,
    String meetingTypeName,
    Instant start,
    Instant end,
    String guestName,
    String guestEmail,
    OffsetDateTime createdAt) {}
