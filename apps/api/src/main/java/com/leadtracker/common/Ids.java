package com.leadtracker.common;

import java.util.UUID;

public final class Ids {

  private Ids() {}

  /** Parses a UUID, or returns null if the text isn't one. */
  public static UUID parse(String value) {
    if (value == null) return null;
    try {
      UUID id = UUID.fromString(value.trim());
      // UUID.fromString accepts short forms like "1-1-1-1-1"; require the canonical form.
      return id.toString().equalsIgnoreCase(value.trim()) ? id : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Parses a UUID from a URL, answering 404 for anything that isn't one. */
  public static UUID pathId(String value, String what) {
    UUID id = parse(value);
    if (id == null) throw ApiException.notFound(what);
    return id;
  }
}
