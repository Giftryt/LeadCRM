package com.leadtracker.lead;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A lead as returned by the API. Dates are YYYY-MM-DD, timestamps ISO-8601 UTC, money in rupees. */
public record LeadDto(
    UUID id,
    String name,
    String company,
    String email,
    String phone,
    LeadSource source,
    Stage stage,
    double valueInr,
    LocalDate nextFollowUp,
    String notes,
    String lostReason,
    UserRef owner,
    UserRef createdBy,
    Instant stageChangedAt,
    Instant wonAt,
    Instant lastContactAt,
    Instant createdAt,
    Instant updatedAt) {

  /** A compact reference to a user (owner, creator, activity author). */
  public record UserRef(UUID id, String name, String email) {}
}
