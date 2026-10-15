package com.leadtracker.activity;

import java.util.Set;

public enum ActivityType {
  CALL,
  EMAIL,
  MEETING,
  NOTE,
  /** Recorded automatically when a lead's stage changes. */
  STAGE_CHANGE,
  /** Recorded automatically when a lead is added. */
  CREATED;

  /** Types a person can log by hand. */
  public static final Set<ActivityType> LOGGABLE = Set.of(CALL, EMAIL, MEETING, NOTE);
}
