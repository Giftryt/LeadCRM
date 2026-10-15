package com.leadtracker.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date helpers. Follow-up dates are calendar dates (YYYY-MM-DD). "Today" is decided in the business
 * timezone (APP_TIMEZONE), not the server's timezone.
 */
public final class Dates {

  private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
  private static final Pattern INDIAN = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
  private static final Pattern STRICT_ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

  private Dates() {}

  public static LocalDate today(Clock clock, ZoneId zone) {
    return LocalDate.ofInstant(clock.instant(), zone);
  }

  /** The instant the current calendar month began in {@code zone}. */
  public static Instant startOfMonth(Clock clock, ZoneId zone) {
    return today(clock, zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
  }

  /** Parses a strict YYYY-MM-DD date, or returns null if it isn't one (including impossible dates). */
  public static LocalDate parseIso(String value) {
    if (value == null || !STRICT_ISO.matcher(value).matches()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** Accepts YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY or DD.MM.YYYY (Indian convention). Null if unrecognised. */
  public static LocalDate parseFlexible(String input) {
    if (input == null) return null;
    String s = input.trim();
    if (s.isEmpty()) return null;
    Matcher m = ISO.matcher(s);
    if (m.matches()) return of(m.group(1), m.group(2), m.group(3));
    m = INDIAN.matcher(s);
    if (m.matches()) return of(m.group(3), m.group(2), m.group(1));
    return null;
  }

  private static LocalDate of(String y, String mo, String d) {
    try {
      return LocalDate.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
    } catch (RuntimeException e) {
      return null;
    }
  }
}
