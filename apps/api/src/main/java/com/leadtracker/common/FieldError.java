package com.leadtracker.common;

/** One invalid field in a validation error: {@code {"field": "email", "message": "is not a valid email address"}}. */
public record FieldError(String field, String message) {}
