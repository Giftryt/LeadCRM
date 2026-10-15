package com.leadtracker.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry in a lead's history: a logged call/email/meeting/note, or an automatic event. */
@Entity
@Table(name = "activities")
public class Activity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "lead_id", nullable = false)
  private UUID leadId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityType type;

  @Column(nullable = false)
  private String body;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Activity() {}

  public Activity(UUID leadId, ActivityType type, String body, UUID userId, Instant createdAt) {
    this.leadId = leadId;
    this.type = type;
    this.body = body;
    this.userId = userId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public ActivityType getType() {
    return type;
  }

  public String getBody() {
    return body;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
