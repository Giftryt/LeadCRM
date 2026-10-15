/**
 * Date helpers. Follow-up dates are calendar dates ("YYYY-MM-DD") stored in a DATE column.
 * "Today" is decided in the business timezone (APP_TIMEZONE), not the server's timezone.
 */

const YMD = /^\d{4}-\d{2}-\d{2}$/;

export function isYmd(value: string): boolean {
  if (!YMD.test(value)) return false;
  const d = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === value;
}

export function todayInTz(tz: string, now: Date = new Date()): string {
  // en-CA formats as YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

export function addDays(ymd: string, days: number): string {
  const d = new Date(`${ymd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export const toDbDate = (ymd: string): Date => new Date(`${ymd}T00:00:00Z`);
export const fromDbDate = (d: Date | null | undefined): string | null =>
  d ? d.toISOString().slice(0, 10) : null;

/** Milliseconds the timezone is ahead of UTC at the given instant. */
function tzOffsetMs(tz: string, at: Date): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(at);
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value);
  const asUtc = Date.UTC(
    get("year"),
    get("month") - 1,
    get("day"),
    get("hour"),
    get("minute"),
    get("second"),
  );
  return asUtc - Math.floor(at.getTime() / 1000) * 1000;
}

/** The instant that local midnight of `ymd` occurs in `tz`. */
export function zonedMidnight(ymd: string, tz: string): Date {
  const guess = new Date(`${ymd}T00:00:00Z`);
  return new Date(guess.getTime() - tzOffsetMs(tz, guess));
}

/** Start of the current calendar month in the business timezone, as an instant. */
export function startOfMonthInTz(tz: string, now: Date = new Date()): Date {
  return zonedMidnight(`${todayInTz(tz, now).slice(0, 7)}-01`, tz);
}

/** Accepts YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY or DD.MM.YYYY (Indian convention). Returns YYYY-MM-DD or null. */
export function parseFlexibleDate(input: string): string | null {
  const s = input.trim();
  if (!s) return null;
  let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (m) {
    const v = `${m[1]}-${m[2].padStart(2, "0")}-${m[3].padStart(2, "0")}`;
    return isYmd(v) ? v : null;
  }
  m = s.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/);
  if (m) {
    const v = `${m[3]}-${m[2].padStart(2, "0")}-${m[1].padStart(2, "0")}`;
    return isYmd(v) ? v : null;
  }
  return null;
}
