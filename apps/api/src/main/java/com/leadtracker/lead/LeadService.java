package com.leadtracker.lead;

import com.leadtracker.activity.Activity;
import com.leadtracker.activity.ActivityDto;
import com.leadtracker.activity.ActivityRepository;
import com.leadtracker.activity.ActivityType;
import com.leadtracker.auth.AuthUser;
import com.leadtracker.common.ApiException;
import com.leadtracker.user.User;
import com.leadtracker.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lead writes. Every change is flushed before reading back through {@link LeadQueries}, which uses plain SQL in
 * the same transaction.
 */
@Service
public class LeadService {

  private final LeadRepository leads;
  private final ActivityRepository activities;
  private final UserRepository users;
  private final LeadQueries queries;
  private final Clock clock;

  public LeadService(
      LeadRepository leads, ActivityRepository activities, UserRepository users, LeadQueries queries, Clock clock) {
    this.leads = leads;
    this.activities = activities;
    this.users = users;
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional
  public LeadDto create(LeadInput in, AuthUser me) {
    assertActiveOwner(in.ownerId());
    Instant now = clock.instant();
    Lead lead = new Lead();
    in.applyTo(lead);
    lead.moveTo(in.stage(), now);
    lead.setCreatedById(me.id());
    lead.setCreatedAt(now);
    lead.setUpdatedAt(now);
    leads.saveAndFlush(lead);
    activities.saveAndFlush(
        new Activity(lead.getId(), ActivityType.CREATED, "Lead added in " + in.stage().label(), me.id(), now));
    return queries.find(lead.getId()).orElseThrow();
  }

  @Transactional
  public LeadDto update(UUID id, LeadInput in, AuthUser me) {
    Lead lead = leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead"));
    if (in.has("ownerId")) assertActiveOwner(in.ownerId());
    Instant now = clock.instant();
    Stage previous = lead.getStage();
    in.applyTo(lead);
    boolean stageChanged = in.has("stage") && in.stage() != previous;
    if (stageChanged) lead.moveTo(in.stage(), now);
    lead.setUpdatedAt(now);
    leads.saveAndFlush(lead);
    if (stageChanged) {
      activities.saveAndFlush(
          new Activity(
              id,
              ActivityType.STAGE_CHANGE,
              "Moved from " + previous.label() + " to " + in.stage().label(),
              me.id(),
              now));
    }
    return queries.find(id).orElseThrow();
  }

  /** Admins can delete any lead; reps only leads they own or created. Activity is removed with the lead. */
  @Transactional
  public void delete(UUID id, AuthUser me) {
    Lead lead = leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead"));
    boolean mine = me.id().equals(lead.getOwnerId()) || me.id().equals(lead.getCreatedById());
    if (!me.isAdmin() && !mine) {
      throw ApiException.forbidden("Only an admin, the lead's owner or the person who added it can delete it.");
    }
    leads.delete(lead);
    leads.flush();
  }

  public record LoggedActivity(ActivityDto activity, LeadDto lead) {}

  /**
   * Logs a call, email, meeting or note. Calls, emails and meetings update the last-contact time. When
   * {@code nextFollowUp} is present (even if empty) the lead's next follow-up is set or cleared in the same step.
   */
  @Transactional
  public LoggedActivity logActivity(
      UUID id, ActivityType type, String body, Optional<LocalDate> nextFollowUp, boolean followUpGiven, AuthUser me) {
    Lead lead = leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead"));
    Instant now = clock.instant();
    Activity activity = activities.saveAndFlush(new Activity(id, type, body, me.id(), now));
    if (type != ActivityType.NOTE) lead.setLastContactAt(now);
    if (followUpGiven) lead.setNextFollowUp(nextFollowUp.orElse(null));
    lead.setUpdatedAt(now);
    leads.saveAndFlush(lead);
    return new LoggedActivity(queries.activity(activity.getId()).orElseThrow(), queries.find(id).orElseThrow());
  }

  private void assertActiveOwner(UUID ownerId) {
    if (ownerId == null) return;
    Optional<User> owner = users.findById(ownerId);
    if (owner.isEmpty() || !owner.get().isActive()) {
      throw ApiException.validation("ownerId", "must be an active user");
    }
  }
}
