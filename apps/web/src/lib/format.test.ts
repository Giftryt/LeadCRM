import { describe, expect, it } from "vitest";
import { addDaysYmd, dueState, inrFull, inrShort, initials } from "./format";

describe("format", () => {
  it("formats rupees in lakh and crore", () => {
    expect(inrShort(0)).toBe("₹0");
    expect(inrShort(95000)).toBe("₹95,000");
    expect(inrShort(250000)).toBe("₹2.5 L");
    expect(inrShort(1200000)).toBe("₹12 L");
    expect(inrShort(35000000)).toBe("₹3.5 Cr");
    expect(inrFull(1250000)).toBe("₹12,50,000");
  });

  it("classifies follow-up dates", () => {
    expect(dueState("2026-10-14", true, "2026-10-15")).toBe("overdue");
    expect(dueState("2026-10-15", true, "2026-10-15")).toBe("today");
    expect(dueState("2026-10-20", true, "2026-10-15")).toBe("later");
    expect(dueState("2026-10-14", false, "2026-10-15")).toBeNull();
    expect(dueState(null, true, "2026-10-15")).toBeNull();
  });

  it("adds days and builds initials", () => {
    expect(addDaysYmd("2026-10-30", 3)).toBe("2026-11-02");
    expect(initials("Ananya Rao")).toBe("AR");
    expect(initials("")).toBe("?");
  });
});
