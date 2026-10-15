package com.leadtracker.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns exceptions into the standard JSON error body. */
@RestControllerAdvice
public class ApiErrorHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(ErrorBody.of(e.code(), e.getMessage(), e.details()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
    if (e.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
      String field =
          mie.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .filter(f -> f != null)
              .collect(Collectors.joining("."));
      FieldError fe = new FieldError(field, "has an invalid value");
      return ResponseEntity.badRequest()
          .body(ErrorBody.of("validation_error", field + ": has an invalid value", List.of(fe)));
    }
    return ResponseEntity.badRequest().body(ErrorBody.of("bad_json", "The request body is not valid JSON."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ErrorBody.of("file_too_large", "The file is larger than 5 MB."));
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ErrorBody> multipart(MultipartException e) {
    return ResponseEntity.badRequest().body(ErrorBody.of("bad_request", "Attach a CSV file in the 'file' field."));
  }

  @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  ResponseEntity<ErrorBody> badParam(Exception e) {
    return ResponseEntity.badRequest().body(ErrorBody.of("bad_request", "A query parameter is missing or invalid."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException e) {
    String msg = String.valueOf(e.getMostSpecificCause().getMessage());
    if (msg.contains("duplicate key")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ErrorBody.of("conflict", "A record with that value already exists."));
    }
    log.warn("Data integrity violation: {}", msg);
    return ResponseEntity.badRequest().body(ErrorBody.of("bad_request", "The data violates a database rule."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorBody> noResource(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorBody.of("not_found", "No such endpoint."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorBody> method(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ErrorBody.of("method_not_allowed", "That method isn't supported on this endpoint."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorBody> mediaType(HttpMediaTypeNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ErrorBody.of("unsupported_media_type", "Send the request body as JSON."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e) {
    log.error("Unhandled error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorBody.of("internal", "Something went wrong on the server."));
  }
}
