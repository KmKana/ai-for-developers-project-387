package io.hexlet.callbooking.api.dto;

import java.time.OffsetDateTime;

/** Свободный Слот, который можно занять. */
public record SlotDto(OffsetDateTime start, OffsetDateTime end) {}
