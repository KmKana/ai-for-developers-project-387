package io.hexlet.callbooking.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Тип встречи в каталоге владельца. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeetingTypeDto(String id, String name, String description, int durationMinutes) {}
