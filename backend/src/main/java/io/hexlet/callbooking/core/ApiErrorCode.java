package io.hexlet.callbooking.core;

/**
 * Коды ошибок из контракта {@code ApiErrorCode}. Значения дублируют TypeSpec как строки, потому что
 * Java не может импортировать сгенерированный TypeScript union; трассировку цепочки проверяет
 * {@code scripts/check-contract-artifacts.mjs}.
 */
public enum ApiErrorCode {
  VALIDATION_ERROR("VALIDATION_ERROR", null),
  MEETING_TYPE_NOT_FOUND("MEETING_TYPE_NOT_FOUND", null),
  SLOT_CONFLICT("SLOT_CONFLICT", "Выбранное время уже занято. Выберите другой слот"),
  OFF_GRID("OFF_GRID", "Начало встречи должно быть на 30-минутной сетке. Выберите другое время."),
  OUTSIDE_WORKING_HOURS(
      "OUTSIDE_WORKING_HOURS",
      "Встреча должна проходить в рабочие дни с 09:00 до 18:00 по времени владельца."),
  OUT_OF_WINDOW("OUT_OF_WINDOW", "Запись доступна на ближайшие 14 дней. Выберите другую дату."),
  INTERNAL_ERROR("INTERNAL_ERROR", "Внутренняя ошибка сервера");

  private final String value;
  private final String defaultMessage;

  ApiErrorCode(String value, String defaultMessage) {
    this.value = value;
    this.defaultMessage = defaultMessage;
  }

  /** Строковый код из OpenAPI. */
  public String value() {
    return value;
  }

  /** Фиксированное сообщение кода; {@code null}, если сообщение зависит от запроса. */
  public String defaultMessage() {
    return defaultMessage;
  }
}
