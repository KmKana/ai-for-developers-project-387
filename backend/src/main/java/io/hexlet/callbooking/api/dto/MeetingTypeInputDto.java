package io.hexlet.callbooking.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Данные для создания типа встречи. */
public record MeetingTypeInputDto(
    String name,
    String description,
    @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer durationMinutes) {}
