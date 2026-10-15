package com.leadtracker.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadtracker.common.ApiException;
import com.leadtracker.common.Dates;
import com.leadtracker.common.FieldError;
import com.leadtracker.common.Ids;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validated lead fields from a create (POST) or partial update (PATCH) body.
 *
 * <p>Parsed from raw JSON so a PATCH can tell "field absent" (leave unchanged) from "field: null" (clear it).
 * All problems are collected and reported together as one validation error.
 */
public final class LeadInput {

  public static final BigDecimal MAX_VALUE = new BigDecimal("999999999999");
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final Set<String> present = new HashSet<>();
  String name;
  String company;
  String email;
  String phone;
  LeadSource source;
  Stage stage;
  BigDecimal valueInr;
  LocalDate nextFollowUp;
  String notes;
  String lostReason;
  UUID ownerId;

  private LeadInput() {}

  public boolean has(String field) {
    return present.contains(field);
  }

  public Stage stage() {
    return stage;
  }

  public UUID ownerId() {
    return ownerId;
  }

  public static LeadInput parse(JsonNode body, boolean create) {
    if (body == null || !body.isObject()) throw ApiException.badRequest("Send the lead as a JSON object.");
    LeadInput in = new LeadInput();
    List<FieldError> errors = new ArrayList<>();

    if (body.has("name")) {
      in.present.add("name");
      int before = errors.size();
      in.name = text(body, "name", 200, errors);
      if (in.name == null && errors.size() == before) errors.add(new FieldError("name", "is required"));
    } else if (create) {
      errors.add(new FieldError("name", "is required"));
    }

    if (body.has("company")) {
      in.present.add("company");
      in.company = text(body, "company", 200, errors);
    }
    if (body.has("email")) {
      in.present.add("email");
      String v = text(body, "email", 254, errors);
      if (v != null) {
        v = v.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(v).matches()) errors.add(new FieldError("email", "is not a valid email address"));
      }
      in.email = v;
    }
    if (body.has("phone")) {
      in.present.add("phone");
      in.phone = text(body, "phone", 40, errors);
    }
    if (body.has("notes")) {
      in.present.add("notes");
      in.notes = text(body, "notes", 10_000, errors);
    }
    if (body.has("lostReason")) {
      in.present.add("lostReason");
      in.lostReason = text(body, "lostReason", 500, errors);
    }

    if (body.has("source")) {
      in.present.add("source");
      in.source = enumValue(body, "source", LeadSource.class, errors);
    } else if (create) {
      in.source = LeadSource.WEBSITE;
    }
    if (body.has("stage")) {
      in.present.add("stage");
      in.stage = enumValue(body, "stage", Stage.class, errors);
    } else if (create) {
      in.stage = Stage.NEW;
    }

    if (body.has("valueInr") && !body.get("valueInr").isNull()) {
      in.present.add("valueInr");
      in.valueInr = money(body.get("valueInr"), errors);
    } else if (create || body.has("valueInr")) {
      in.present.add("valueInr");
      in.valueInr = BigDecimal.ZERO.setScale(2);
    }

    if (body.has("nextFollowUp")) {
      in.present.add("nextFollowUp");
      JsonNode n = body.get("nextFollowUp");
      if (!n.isNull() && !(n.isTextual() && n.asText().isEmpty())) {
        LocalDate d = n.isTextual() ? Dates.parseIso(n.asText()) : null;
        if (d == null) errors.add(new FieldError("nextFollowUp", "must be a date in YYYY-MM-DD format"));
        in.nextFollowUp = d;
      }
    }

    if (body.has("ownerId")) {
      in.present.add("ownerId");
      JsonNode n = body.get("ownerId");
      if (!n.isNull() && !(n.isTextual() && n.asText().isEmpty())) {
        UUID id = n.isTextual() ? Ids.parse(n.asText()) : null;
        if (id == null) errors.add(new FieldError("ownerId", "must be a valid user id"));
        in.ownerId = id;
      }
    }

    if (!errors.isEmpty()) throw ApiException.validation(errors);
    return in;
  }

  /** Copies the present fields onto the entity. Stage is handled by the caller (it has side effects). */
  public void applyTo(Lead lead) {
    if (has("name")) lead.setName(name);
    if (has("company")) lead.setCompany(company);
    if (has("email")) lead.setEmail(email);
    if (has("phone")) lead.setPhone(phone);
    if (has("source") || source != null) lead.setSource(source);
    if (has("valueInr")) lead.setValueInr(valueInr);
    if (has("nextFollowUp")) lead.setNextFollowUp(nextFollowUp);
    if (has("notes")) lead.setNotes(notes);
    if (has("lostReason")) lead.setLostReason(lostReason);
    if (has("ownerId")) lead.setOwnerId(ownerId);
  }

  /* ---------- field helpers ---------- */

  private static String text(JsonNode body, String field, int max, List<FieldError> errors) {
    JsonNode n = body.get(field);
    if (n == null || n.isNull()) return null;
    if (!n.isTextual()) {
      errors.add(new FieldError(field, "must be text"));
      return null;
    }
    String v = n.asText().trim();
    if (v.isEmpty()) return null;
    if (v.length() > max) {
      errors.add(new FieldError(field, "must be at most " + max + " characters"));
      return null;
    }
    return v;
  }

  private static <E extends Enum<E>> E enumValue(JsonNode body, String field, Class<E> type, List<FieldError> errors) {
    JsonNode n = body.get(field);
    if (n != null && n.isTextual()) {
      for (E e : type.getEnumConstants()) {
        if (e.name().equals(n.asText())) return e;
      }
    }
    List<String> names = new ArrayList<>();
    for (E e : type.getEnumConstants()) names.add(e.name());
    errors.add(new FieldError(field, "must be one of " + String.join(", ", names)));
    return null;
  }

  private static BigDecimal money(JsonNode n, List<FieldError> errors) {
    BigDecimal v = null;
    if (n.isNumber()) {
      v = n.decimalValue();
    } else if (n.isTextual()) {
      try {
        v = new BigDecimal(n.asText().trim());
      } catch (NumberFormatException e) {
        v = null;
      }
    }
    if (v == null) {
      errors.add(new FieldError("valueInr", "must be a number"));
      return null;
    }
    if (v.signum() < 0) {
      errors.add(new FieldError("valueInr", "must be 0 or more"));
      return null;
    }
    if (v.compareTo(MAX_VALUE) > 0) {
      errors.add(new FieldError("valueInr", "must be at most 999999999999"));
      return null;
    }
    return v.setScale(2, RoundingMode.HALF_UP);
  }
}
