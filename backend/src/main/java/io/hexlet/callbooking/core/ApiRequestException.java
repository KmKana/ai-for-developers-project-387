package io.hexlet.callbooking.core;

/** Ошибка запроса: код и HTTP-статус (400 — некорректные данные, 409 — время недоступно). */
public class ApiRequestException extends RuntimeException {

  private final String code;
  private final int status;

  public ApiRequestException(int status, String code, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  /** Код из контракта с фиксированным сообщением. */
  public ApiRequestException(int status, ApiErrorCode code) {
    this(status, code.value(), code.defaultMessage());
  }

  /** Код из контракта с сообщением, зависящим от запроса. */
  public ApiRequestException(int status, ApiErrorCode code, String message) {
    this(status, code.value(), message);
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
