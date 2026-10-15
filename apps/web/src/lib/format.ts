/** ₹ amounts in lakh/crore shorthand, e.g. 2,50,000 → "₹2.5 L", 3,50,00,000 → "₹3.5 Cr". */
export function inrShort(n: number): string {
  const v = Number(n) || 0;
  const a = Math.abs(v);
  const trim = (x: string) => x.replace(/\.0$/, "");
  if (a >= 1e7) return `₹${trim((v / 1e7).toFixed(a >= 1e8 ? 0 : 1))} Cr`;
  if (a >= 1e5) return `₹${trim((v / 1e5).toFixed(a >= 1e6 ? 0 : 1))} L`;
  return `₹${v.toLocaleString("en-IN")}`;
}

/** Full ₹ amount with Indian digit grouping, e.g. "₹12,50,000". */
export const inrFull = (n: number) =>
  `₹${(Number(n) || 0).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;

const pad = (n: number) => String(n).padStart(2, "0");
export const ymd = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const todayYmd = () => ymd(new Date());

export function addDaysYmd(base: string, days: number): string {
  const d = new Date(`${base}T00:00:00`);
  d.setDate(d.getDate() + days);
  return ymd(d);
}

export function formatDay(s: string | null | undefined): string {
  if (!s) return "";
  return new Date(`${s}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

export type DueState = "overdue" | "today" | "later" | null;
export function dueState(nextFollowUp: string | null, open: boolean, today = todayYmd()): DueState {
  if (!nextFollowUp || !open) return null;
  if (nextFollowUp < today) return "overdue";
  if (nextFollowUp === today) return "today";
  return "later";
}

export function initials(name: string | null | undefined): string {
  const parts = (name ?? "").trim().split(/\s+/).filter(Boolean);
  return (
    parts
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join("") || "?"
  );
}
