package io.hexlet.callbooking.api;

import io.hexlet.callbooking.api.dto.ApiErrorResponse;
import io.hexlet.callbooking.core.ApiErrorCode;
import io.hexlet.callbooking.core.ApiRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Преобразование ошибок в ответы контракта {code, message}. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleApiRequest(ApiRequestException ex) {
    return ResponseEntity.status(ex.status())
        .body(new ApiErrorResponse(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    // Jackson оборачивает ошибки десериализации; собственное сообщение DTO должно дойти до клиента.
    Throwable cause = ex;
    while (cause != null) {
      if (cause instanceof ApiRequestException api) {
        return handleApiRequest(api);
      }
      cause = cause.getCause();
    }
    return ResponseEntity.badRequest()
        .body(
            new ApiErrorResponse(
                ApiErrorCode.VALIDATION_ERROR.value(),
                "Некорректное тело запроса: проверьте формат JSON и дат"));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiErrorResponse> handleMissingParam(
      MissingServletRequestParameterException ex) {
    return ResponseEntity.badRequest()
        .body(
            new ApiErrorResponse(
                ApiErrorCode.VALIDATION_ERROR.value(),
                "Не передан обязательный параметр: " + ex.getParameterName()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.badRequest()
        .body(
            new ApiErrorResponse(
                ApiErrorCode.VALIDATION_ERROR.value(),
                "Некорректный параметр запроса: " + ex.getName()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ApiErrorResponse(
                ApiErrorCode.INTERNAL_ERROR.value(), ApiErrorCode.INTERNAL_ERROR.defaultMessage()));
  }
}
