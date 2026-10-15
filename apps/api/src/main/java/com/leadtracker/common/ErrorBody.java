package com.leadtracker.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The error envelope every failed request returns: {@code {"error": {"code", "message", "details"}}}. */
public record ErrorBody(Error error) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Error(String code, String message, List<FieldError> details) {}

  public static ErrorBody of(String code, String message) {
    return new ErrorBody(new Error(code, message, null));
  }

  public static ErrorBody of(String code, String message, List<FieldError> details) {
    return new ErrorBody(new Error(code, message, details));
  }
}
