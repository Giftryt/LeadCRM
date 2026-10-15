package com.leadtracker.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** An error that maps directly to an HTTP response with the standard error body. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<FieldError> details;

  public ApiException(HttpStatus status, String code, String message, List<FieldError> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, null);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<FieldError> details() {
    return details;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", message);
  }

  /** A validation failure; the message is the first problem as "field: message". */
  public static ApiException validation(List<FieldError> details) {
    FieldError first = details.get(0);
    return new ApiException(
        HttpStatus.BAD_REQUEST, "validation_error", first.field() + ": " + first.message(), details);
  }

  public static ApiException validation(String field, String message) {
    return validation(List.of(new FieldError(field, message)));
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
  }

  public static ApiException forbidden() {
    return forbidden("You don't have permission to do that.");
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found.");
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "conflict", message);
  }
}
