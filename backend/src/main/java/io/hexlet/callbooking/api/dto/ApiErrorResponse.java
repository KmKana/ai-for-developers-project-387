package io.hexlet.callbooking.api.dto;

/** Ошибка API: машинный код и понятное сообщение. */
public record ApiErrorResponse(String code, String message) {}
