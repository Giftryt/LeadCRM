package com.leadtracker.lead;

import com.leadtracker.activity.Activity;
import com.leadtracker.activity.ActivityRepository;
import com.leadtracker.activity.ActivityType;
import com.leadtracker.auth.AuthUser;
import com.leadtracker.common.ApiException;
import com.leadtracker.common.Csv;
import com.leadtracker.common.Dates;
import com.leadtracker.user.User;
import com.leadtracker.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bulk-creates leads from a CSV file with flexible headers, skipping and reporting bad rows. */
@Service
public class LeadImportService {

  public static final int MAX_ROWS = 5000;
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  /** Accepted header names (lower case) for each field. */
  private static final Map<String, List<String>> HEADERS = new LinkedHashMap<>();

  static {
    HEADERS.put("name", List.of("name", "contact", "contact name", "lead", "full name"));
    HEADERS.put("company", List.of("company", "organisation", "organization", "account", "company name"));
    HEADERS.put("email", List.of("email", "e-mail", "email address"));
    HEADERS.put("phone", List.of("phone", "mobile", "phone number", "contact number", "mobile number"));
    HEADERS.put("source", List.of("source", "lead source"));
    HEADERS.put("stage", List.of("stage", "status"));
    HEADERS.put("value", List.of("value", "value (inr)", "deal value", "amount", "deal size"));
    HEADERS.put(
        "nextFollowUp", List.of("next follow-up", "next follow up", "follow-up", "follow up", "followup", "next followup"));
    HEADERS.put("notes", List.of("notes", "note", "comments"));
    HEADERS.put("owner", List.of("owner", "owner email", "assigned to"));
  }

  public record RowError(int row, String message) {}

  public record ImportResult(int imported, int skipped, List<RowError> errors) {}

  private final LeadRepository leads;
  private final ActivityRepository activities;
  private final UserRepository users;
  private final Clock clock;

  public LeadImportService(LeadRepository leads, ActivityRepository activities, UserRepository users, Clock clock) {
    this.leads = leads;
    this.activities = activities;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public ImportResult importCsv(String text, AuthUser me) {
    List<List<String>> rows;
    try {
      rows = Csv.parse(text);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("The file couldn't be read as CSV: " + e.getMessage());
    }
    if (rows.size() < 2) throw ApiException.badRequest("The file needs a header row and at least one lead.");
    if (rows.size() - 1 > MAX_ROWS) throw ApiException.badRequest("Import at most " + MAX_ROWS + " leads at a time.");

    List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    Map<String, Integer> col = new HashMap<>();
    HEADERS.forEach(
        (field, names) -> {
          for (int i = 0; i < header.size(); i++) {
            if (names.contains(header.get(i))) {
              col.put(field, i);
              return;
            }
          }
        });
    if (!col.containsKey("name") && !col.containsKey("company")) {
      throw ApiException.badRequest("Couldn't find a Name or Company column. Rename a header to Name and try again.");
    }

    // Owners can be referenced by email or name.
    Map<String, UUID> owners = new HashMap<>();
    for (User u : users.findByActiveTrueOrderByNameAsc()) {
      owners.put(u.getEmail().toLowerCase(Locale.ROOT), u.getId());
      owners.putIfAbsent(u.getName().toLowerCase(Locale.ROOT), u.getId());
    }

    Instant now = clock.instant();
    List<RowError> errors = new ArrayList<>();
    List<Lead> toCreate = new ArrayList<>();
    for (int r = 1; r < rows.size(); r++) {
      List<String> row = rows.get(r);
      int rowNo = r + 1; // 1-based, counting the header
      String name = firstNonEmpty(get(row, col, "name"), get(row, col, "company"));
      if (name.isEmpty()) {
        errors.add(new RowError(rowNo, "Missing name and company"));
        continue;
      }
      String email = get(row, col, "email").toLowerCase(Locale.ROOT);
      if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
        errors.add(new RowError(rowNo, "Invalid email \"" + email + "\""));
        continue;
      }
      String rawDate = get(row, col, "nextFollowUp");
      LocalDate followUp = Dates.parseFlexible(rawDate);
      if (!rawDate.isEmpty() && followUp == null) {
        errors.add(new RowError(rowNo, "Unrecognised date \"" + rawDate + "\""));
        continue;
      }
      BigDecimal value = parseMoney(get(row, col, "value"));
      if (value.compareTo(LeadInput.MAX_VALUE) > 0) {
        errors.add(new RowError(rowNo, "Value is too large"));
        continue;
      }
      Stage stage = Stage.match(get(row, col, "stage"));
      LeadSource source = LeadSource.match(get(row, col, "source"));
      String ownerRaw = get(row, col, "owner").toLowerCase(Locale.ROOT);

      Lead lead = new Lead();
      lead.setName(truncate(name, 200));
      lead.setCompany(emptyToNull(truncate(get(row, col, "company"), 200)));
      lead.setEmail(emptyToNull(email));
      lead.setPhone(emptyToNull(truncate(get(row, col, "phone"), 40)));
      lead.setSource(source == null ? LeadSource.WEBSITE : source);
      lead.setValueInr(value);
      lead.setNextFollowUp(followUp);
      lead.setNotes(emptyToNull(truncate(get(row, col, "notes"), 10_000)));
      lead.setOwnerId(ownerRaw.isEmpty() ? me.id() : owners.get(ownerRaw));
      lead.setCreatedById(me.id());
      lead.moveTo(stage == null ? Stage.NEW : stage, now);
      lead.setCreatedAt(now);
      lead.setUpdatedAt(now);
      toCreate.add(lead);
    }

    List<Lead> saved = leads.saveAll(toCreate);
    leads.flush();
    activities.saveAll(
        saved.stream()
            .map(l -> new Activity(l.getId(), ActivityType.CREATED, "Imported from CSV", me.id(), now))
            .toList());
    activities.flush();
    return new ImportResult(saved.size(), errors.size(), errors.size() > 100 ? errors.subList(0, 100) : errors);
  }

  private static String get(List<String> row, Map<String, Integer> col, String field) {
    Integer i = col.get(field);
    if (i == null || i >= row.size()) return "";
    String v = row.get(i);
    return v == null ? "" : v.trim();
  }

  private static BigDecimal parseMoney(String raw) {
    String digits = raw.replaceAll("[^\\d.]", "");
    if (digits.isEmpty()) return BigDecimal.ZERO.setScale(2);
    try {
      return new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO.setScale(2);
    }
  }

  private static String firstNonEmpty(String a, String b) {
    return !a.isEmpty() ? a : b;
  }

  private static String emptyToNull(String v) {
    return v == null || v.isEmpty() ? null : v;
  }

  private static String truncate(String v, int max) {
    return v.length() > max ? v.substring(0, max) : v;
  }
}
