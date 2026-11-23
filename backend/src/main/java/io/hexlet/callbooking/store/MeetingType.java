package io.hexlet.callbooking.store;

/** Тип встречи владельца (внутреннее представление). */
public record MeetingType(String id, String name, String description, int durationMinutes) {}
