package com.leadtracker.seed;

import com.leadtracker.activity.Activity;
import com.leadtracker.activity.ActivityRepository;
import com.leadtracker.activity.ActivityType;
import com.leadtracker.common.Dates;
import com.leadtracker.config.AppProperties;
import com.leadtracker.lead.Lead;
import com.leadtracker.lead.LeadRepository;
import com.leadtracker.lead.LeadSource;
import com.leadtracker.lead.Stage;
import com.leadtracker.user.Role;
import com.leadtracker.user.User;
import com.leadtracker.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup: creates the first admin from SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD when there are no users yet,
 * and adds sample leads when SEED_SAMPLE_LEADS=true and there are no leads. Safe to leave configured; it does
 * nothing once data exists.
 */
@Component
public class DataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UserRepository users;
  private final LeadRepository leads;
  private final ActivityRepository activities;
  private final PasswordEncoder encoder;
  private final AppProperties props;
  private final Clock clock;

  public DataSeeder(
      UserRepository users,
      LeadRepository leads,
      ActivityRepository activities,
      PasswordEncoder encoder,
      AppProperties props,
      Clock clock) {
    this.users = users;
    this.leads = leads;
    this.activities = activities;
    this.encoder = encoder;
    this.props = props;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    AppProperties.Seed seed = props.seed();
    if (users.count() == 0) {
      String email = seed.adminEmail() == null ? "" : seed.adminEmail().trim().toLowerCase(Locale.ROOT);
      String password = seed.adminPassword() == null ? "" : seed.adminPassword();
      if (email.isEmpty() || password.length() < 10) {
        log.warn("No users exist. Set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD (10+ characters) to create the first admin.");
        return;
      }
      String name = seed.adminName() == null || seed.adminName().isBlank() ? "Admin" : seed.adminName().trim();
      users.save(new User(email, name, encoder.encode(password), Role.ADMIN, clock.instant()));
      log.info("Created admin {}. Sign in and change the password under Account.", email);
    }
    if (seed.sampleLeads() && leads.count() == 0) addSampleLeads();
  }

  private void addSampleLeads() {
    User admin = users.findFirstByRole(Role.ADMIN).orElse(null);
    LocalDate today = Dates.today(clock, props.zone());
    Instant now = clock.instant();
    Object[][] samples = {
      {"Ananya Rao", "Brightleaf Logistics", LeadSource.REFERRAL, Stage.QUALIFIED, 450000, -2},
      {"Rohit Mehra", "Kestrel Software", LeadSource.LINKEDIN, Stage.PROPOSAL, 1200000, 0},
      {"Fatima Sheikh", "Tanvi Hospitality Group", LeadSource.EVENT, Stage.NEGOTIATION, 2800000, 2},
      {"Arjun Nair", "Coastline Foods", LeadSource.WEBSITE, Stage.NEW, 180000, 1},
      {"Meera Iyer", "Northstar Clinics", LeadSource.COLD_OUTREACH, Stage.CONTACTED, 650000, -1},
      {"Vikram Desai", "Granite Infra", LeadSource.PARTNER, Stage.WON, 3500000, null},
      {"Sneha Kulkarni", "Pebble Retail", LeadSource.INBOUND_CALL, Stage.LOST, 220000, null},
      {"Kabir Malhotra", "Orbit Edtech", LeadSource.WEBSITE, Stage.QUALIFIED, 900000, 5},
    };
    for (Object[] s : samples) {
      String name = (String) s[0];
      Stage stage = (Stage) s[3];
      Lead lead = new Lead();
      lead.setName(name);
      lead.setCompany((String) s[1]);
      lead.setEmail(name.split(" ")[0].toLowerCase(Locale.ROOT) + "@example.com");
      lead.setSource((LeadSource) s[2]);
      lead.setValueInr(BigDecimal.valueOf((Integer) s[4]));
      lead.setNextFollowUp(s[5] == null ? null : today.plusDays((Integer) s[5]));
      lead.setNotes("Sample lead (development seed).");
      lead.setLostReason(stage == Stage.LOST ? "Went with an in-house option" : null);
      lead.setOwnerId(admin == null ? null : admin.getId());
      lead.setCreatedById(admin == null ? null : admin.getId());
      lead.moveTo(stage, now);
      lead.setCreatedAt(now);
      lead.setUpdatedAt(now);
      leads.saveAndFlush(lead);
      activities.save(
          new Activity(lead.getId(), ActivityType.CREATED, "Sample lead from seed", lead.getCreatedById(), now));
    }
    log.info("Created {} sample leads.", samples.length);
  }
}
