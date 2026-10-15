import { describe, expect, it } from "vitest";
import {
  addDays,
  isYmd,
  parseFlexibleDate,
  startOfMonthInTz,
  todayInTz,
  zonedMidnight,
} from "../src/lib/dates.js";

describe("dates", () => {
  it("decides 'today' in the business timezone", () => {
    // 20:00 UTC on 14 Oct is 01:30 on 15 Oct in India
    const at = new Date("2026-10-14T20:00:00Z");
    expect(todayInTz("Asia/Kolkata", at)).toBe("2026-10-15");
    expect(todayInTz("UTC", at)).toBe("2026-10-14");
  });

  it("finds local midnight and month start", () => {
    expect(zonedMidnight("2026-10-15", "Asia/Kolkata").toISOString()).toBe("2026-10-14T18:30:00.000Z");
    expect(startOfMonthInTz("Asia/Kolkata", new Date("2026-10-15T06:00:00Z")).toISOString()).toBe(
      "2026-09-30T18:30:00.000Z",
    );
  });

  it("adds days across month ends", () => {
    expect(addDays("2026-10-30", 3)).toBe("2026-11-02");
  });

  it("parses Indian and ISO date formats and rejects impossible dates", () => {
    expect(parseFlexibleDate("05/11/2026")).toBe("2026-11-05");
    expect(parseFlexibleDate("5-11-2026")).toBe("2026-11-05");
    expect(parseFlexibleDate("2026-11-05")).toBe("2026-11-05");
    expect(parseFlexibleDate("31/02/2026")).toBeNull();
    expect(parseFlexibleDate("next week")).toBeNull();
    expect(isYmd("2026-13-01")).toBe(false);
  });
});
