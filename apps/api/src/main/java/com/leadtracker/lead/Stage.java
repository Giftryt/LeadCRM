package com.leadtracker.lead;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pipeline stages in order. Keep labels and probabilities in sync with apps/web/src/lib/constants.ts. */
public enum Stage {
  NEW("New", 0.1),
  CONTACTED("Contacted", 0.2),
  QUALIFIED("Qualified", 0.4),
  PROPOSAL("Proposal sent", 0.6),
  NEGOTIATION("Negotiation", 0.8),
  WON("Won", 1.0),
  LOST("Lost", 0.0);

  public static final List<Stage> OPEN = List.of(NEW, CONTACTED, QUALIFIED, PROPOSAL, NEGOTIATION);

  private final String label;
  private final double probability;

  Stage(String label, double probability) {
    this.label = label;
    this.probability = probability;
  }

  public String label() {
    return label;
  }

  /** Used for the weighted forecast. */
  public double probability() {
    return probability;
  }

  public boolean isOpen() {
    return OPEN.contains(this);
  }

  /** Matches free text such as "Proposal sent", "proposal" or "WON". Null if nothing matches. */
  public static Stage match(String input) {
    if (input == null) return null;
    String s = input.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return null;
    return Arrays.stream(values())
        .filter(
            st -> {
              String label = st.label.toLowerCase(Locale.ROOT);
              return st.name().toLowerCase(Locale.ROOT).equals(s) || label.equals(s) || label.startsWith(s);
            })
        .findFirst()
        .orElse(null);
  }
}
