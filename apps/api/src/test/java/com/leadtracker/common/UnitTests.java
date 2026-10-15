package com.leadtracker.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadtracker.lead.LeadSource;
import com.leadtracker.lead.Stage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring context or database. */
class UnitTests {

  private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

  @Test
  void decidesTodayInTheBusinessTimezone() {
    // 20:00 UTC on 14 Oct is 01:30 on 15 Oct in India
    Clock clock = Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"), ZoneOffset.UTC);
    assertThat(Dates.today(clock, INDIA)).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(Dates.today(clock, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2026, 10, 14));
  }

  @Test
  void findsTheStartOfTheMonthInTheBusinessTimezone() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC);
    assertThat(Dates.startOfMonth(clock, INDIA)).isEqualTo(Instant.parse("2026-09-30T18:30:00Z"));
  }

  @Test
  void parsesIndianAndIsoDatesAndRejectsImpossibleOnes() {
    assertThat(Dates.parseFlexible("05/11/2026")).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(Dates.parseFlexible("5-11-2026")).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(Dates.parseFlexible("5.11.2026")).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(Dates.parseFlexible("2026-11-05")).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(Dates.parseFlexible("31/02/2026")).isNull();
    assertThat(Dates.parseFlexible("next week")).isNull();
    assertThat(Dates.parseIso("2026-13-01")).isNull();
    assertThat(Dates.parseIso("2026-1-01")).isNull();
    assertThat(Dates.parseIso("2026-02-28")).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void parsesQuotedCsvWithBomAndBlankLines() {
    List<List<String>> rows = Csv.parse("﻿Name,Value\r\n\"Rao, Ananya\",\"₹2,50,000\"\r\n\r\n\"He said \"\"hi\"\"\",1\n");
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0)).containsExactly("Name", "Value");
    assertThat(rows.get(1)).containsExactly("Rao, Ananya", "₹2,50,000");
    assertThat(rows.get(2)).containsExactly("He said \"hi\"", "1");
    assertThatThrownBy(() -> Csv.parse("a,\"unclosed\n")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void writesCsvCellsSafely() {
    assertThat(Csv.line(List.of("plain", "with, comma", "quote \"x\"", ""))).isEqualTo("plain,\"with, comma\",\"quote \"\"x\"\"\",\n");
    assertThat(Csv.escape(null)).isEmpty();
  }

  @Test
  void matchesStagesAndSourcesFromFreeText() {
    assertThat(Stage.match("Proposal sent")).isEqualTo(Stage.PROPOSAL);
    assertThat(Stage.match("proposal")).isEqualTo(Stage.PROPOSAL);
    assertThat(Stage.match("WON")).isEqualTo(Stage.WON);
    assertThat(Stage.match("")).isNull();
    assertThat(Stage.match("nonsense")).isNull();
    assertThat(LeadSource.match("Cold outreach")).isEqualTo(LeadSource.COLD_OUTREACH);
    assertThat(LeadSource.match("cold_outreach")).isEqualTo(LeadSource.COLD_OUTREACH);
    assertThat(LeadSource.match("Newspaper")).isEqualTo(LeadSource.OTHER);
    assertThat(LeadSource.match(" ")).isNull();
  }

  @Test
  void acceptsOnlyCanonicalUuids() {
    assertThat(Ids.parse("00000000-0000-0000-0000-000000000000")).isNotNull();
    assertThat(Ids.parse("1-1-1-1-1")).isNull();
    assertThat(Ids.parse("nope")).isNull();
  }
}
