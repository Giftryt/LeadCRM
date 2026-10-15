package com.leadtracker.lead;

import java.util.Locale;

public enum LeadSource {
  WEBSITE("Website"),
  REFERRAL("Referral"),
  LINKEDIN("LinkedIn"),
  COLD_OUTREACH("Cold outreach"),
  EVENT("Event"),
  PARTNER("Partner"),
  INBOUND_CALL("Inbound call"),
  OTHER("Other");

  private final String label;

  LeadSource(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** Matches free text ("Cold outreach", "cold_outreach"); unknown non-empty text becomes OTHER, empty is null. */
  public static LeadSource match(String input) {
    if (input == null) return null;
    String s = input.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    if (s.isEmpty()) return null;
    for (LeadSource src : values()) {
      if (src.label.toLowerCase(Locale.ROOT).equals(s) || src.name().toLowerCase(Locale.ROOT).replace('_', ' ').equals(s)) {
        return src;
      }
    }
    return OTHER;
  }
}
