package com.leadtracker.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sales lead. Owner and creator are stored as ids; reads that need names go through {@link LeadQueries}.
 */
@Entity
@Table(name = "leads")
public class Lead {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  private String company;
  private String email;
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadSource source = LeadSource.WEBSITE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Stage stage = Stage.NEW;

  /** Deal value in Indian rupees. */
  @Column(name = "value_inr", nullable = false, precision = 14, scale = 2)
  private BigDecimal valueInr = BigDecimal.ZERO;

  @Column(name = "next_follow_up")
  private LocalDate nextFollowUp;

  private String notes;

  @Column(name = "lost_reason")
  private String lostReason;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(name = "created_by_id")
  private UUID createdById;

  @Column(name = "stage_changed_at", nullable = false)
  private Instant stageChangedAt;

  @Column(name = "won_at")
  private Instant wonAt;

  @Column(name = "last_contact_at")
  private Instant lastContactAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Lead() {}

  /** Sets the stage and the timestamps that go with a stage change. */
  public void moveTo(Stage newStage, Instant now) {
    this.stage = newStage;
    this.stageChangedAt = now;
    if (newStage == Stage.WON) this.wonAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public LeadSource getSource() {
    return source;
  }

  public void setSource(LeadSource source) {
    this.source = source;
  }

  public Stage getStage() {
    return stage;
  }

  public BigDecimal getValueInr() {
    return valueInr;
  }

  public void setValueInr(BigDecimal valueInr) {
    this.valueInr = valueInr;
  }

  public LocalDate getNextFollowUp() {
    return nextFollowUp;
  }

  public void setNextFollowUp(LocalDate nextFollowUp) {
    this.nextFollowUp = nextFollowUp;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getLostReason() {
    return lostReason;
  }

  public void setLostReason(String lostReason) {
    this.lostReason = lostReason;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public UUID getCreatedById() {
    return createdById;
  }

  public void setCreatedById(UUID createdById) {
    this.createdById = createdById;
  }

  public Instant getStageChangedAt() {
    return stageChangedAt;
  }

  public Instant getWonAt() {
    return wonAt;
  }

  public Instant getLastContactAt() {
    return lastContactAt;
  }

  public void setLastContactAt(Instant lastContactAt) {
    this.lastContactAt = lastContactAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
