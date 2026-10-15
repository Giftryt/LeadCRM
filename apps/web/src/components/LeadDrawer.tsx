import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError, type Lead, type LeadInput } from "../api/client";
import {
  useActivities,
  useCreateLead,
  useDeleteLead,
  useLead,
  useLogActivity,
  useUpdateLead,
  useUsers,
} from "../api/hooks";
import { useAuth } from "../auth/AuthContext";
import { ACTIVITY_META, LOGGABLE_ACTIVITIES, SOURCES, STAGES, type ActivityType } from "../lib/constants";
import { addDaysYmd, formatDateTime, todayYmd } from "../lib/format";
import { errorText, useToast } from "./Toast";

type Props = { leadId: string | null; onClose: () => void; onCreated: (id: string) => void };

type Form = {
  name: string;
  company: string;
  email: string;
  phone: string;
  source: LeadInput["source"];
  stage: LeadInput["stage"];
  valueInr: string;
  nextFollowUp: string;
  notes: string;
  lostReason: string;
  ownerId: string;
};

const toForm = (l: Lead): Form => ({
  name: l.name,
  company: l.company ?? "",
  email: l.email ?? "",
  phone: l.phone ?? "",
  source: l.source,
  stage: l.stage,
  valueInr: l.valueInr ? String(l.valueInr) : "",
  nextFollowUp: l.nextFollowUp ?? "",
  notes: l.notes ?? "",
  lostReason: l.lostReason ?? "",
  ownerId: l.owner?.id ?? "",
});

const toInput = (f: Form): LeadInput => ({
  name: f.name.trim(),
  company: f.company.trim() || null,
  email: f.email.trim() || null,
  phone: f.phone.trim() || null,
  source: f.source,
  stage: f.stage,
  valueInr: f.valueInr === "" ? 0 : Math.max(0, Math.round(Number(f.valueInr) || 0)),
  nextFollowUp: f.nextFollowUp || null,
  notes: f.notes.trim() || null,
  lostReason: f.stage === "LOST" ? f.lostReason.trim() || null : null,
  ownerId: f.ownerId || null,
});

export function LeadDrawer({ leadId, onClose, onCreated }: Props) {
  const isNew = leadId === null;
  const { user, canWrite, isAdmin } = useAuth();
  const toast = useToast();
  const { data: lead, error: loadError } = useLead(leadId);
  const { data: users } = useUsers();
  const create = useCreateLead();
  const update = useUpdateLead();
  const del = useDeleteLead();
  const [form, setForm] = useState<Form | null>(
    isNew
      ? {
          name: "",
          company: "",
          email: "",
          phone: "",
          source: "WEBSITE",
          stage: "NEW",
          valueInr: "",
          nextFollowUp: addDaysYmd(todayYmd(), 1),
          notes: "",
          lostReason: "",
          ownerId: user?.id ?? "",
        }
      : null,
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (lead && !form) setForm(toForm(lead));
  }, [lead, form]);
  useEffect(() => {
    if (isNew) nameRef.current?.focus();
  }, [isNew]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => (f ? { ...f, [k]: v } : f));
  const canDelete =
    !!lead && (isAdmin || (canWrite && (lead.owner?.id === user?.id || lead.createdBy?.id === user?.id)));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form) return;
    setFieldErrors({});
    const input = toInput(form);
    if (!input.name) {
      setFieldErrors({ name: "Add a contact name." });
      nameRef.current?.focus();
      return;
    }
    try {
      if (isNew) {
        const created = await create.mutateAsync(input);
        toast("Lead added");
        onCreated(created.id);
      } else {
        const updated = await update.mutateAsync({ id: leadId!, body: input });
        setForm(toForm(updated));
        toast("Saved");
      }
    } catch (err) {
      if (err instanceof ApiError && err.details) {
        setFieldErrors(Object.fromEntries(err.details.map((d) => [d.field, d.message])));
      }
      toast(errorText(err), { error: true });
    }
  }

  async function onDelete() {
    try {
      await del.mutateAsync(leadId!);
      toast(`${lead?.name ?? "Lead"} deleted`);
      onClose();
    } catch (err) {
      toast(errorText(err), { error: true });
    }
  }

  const ro = !canWrite;
  const fieldErr = (k: string) => fieldErrors[k] && <span className="field-err">{fieldErrors[k]}</span>;

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
        <div className="d-head">
          <h2 id="drawer-title">{isNew ? "New lead" : (lead?.name ?? "Lead")}</h2>
          <button className="btn ghost" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="d-body">
          {loadError && <p className="banner">This lead couldn't be loaded. It may have been deleted.</p>}
          {!form && !loadError && <p className="muted">Loading…</p>}
          {form && (
            <form onSubmit={onSubmit} noValidate>
              <fieldset disabled={ro} className="plain">
                <div className="grid2">
                  <div className="fld">
                    <label htmlFor="f-name">
                      Contact name <span className="req">*</span>
                    </label>
                    <input
                      ref={nameRef}
                      id="f-name"
                      className="field"
                      value={form.name}
                      onChange={(e) => set("name", e.target.value)}
                      autoComplete="off"
                    />
                    {fieldErr("name")}
                  </div>
                  <div className="fld">
                    <label htmlFor="f-company">Company</label>
                    <input
                      id="f-company"
                      className="field"
                      value={form.company}
                      onChange={(e) => set("company", e.target.value)}
                      autoComplete="off"
                    />
                  </div>
                  <div className="fld">
                    <label htmlFor="f-email">Email</label>
                    <input
                      id="f-email"
                      className="field"
                      type="email"
                      value={form.email}
                      onChange={(e) => set("email", e.target.value)}
                      autoComplete="off"
                    />
                    {fieldErr("email")}
                  </div>
                  <div className="fld">
                    <label htmlFor="f-phone">Phone</label>
                    <input
                      id="f-phone"
                      className="field"
                      type="tel"
                      value={form.phone}
                      onChange={(e) => set("phone", e.target.value)}
                      placeholder="+91 98xxx xxxxx"
                      autoComplete="off"
                    />
                  </div>
                  <div className="fld">
                    <label htmlFor="f-stage">Stage</label>
                    <select
                      id="f-stage"
                      className="field"
                      value={form.stage}
                      onChange={(e) => set("stage", e.target.value as Form["stage"])}
                    >
                      {STAGES.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="fld">
                    <label htmlFor="f-value">Deal value (₹)</label>
                    <input
                      id="f-value"
                      className="field num"
                      type="number"
                      min={0}
                      step={1000}
                      value={form.valueInr}
                      onChange={(e) => set("valueInr", e.target.value)}
                      placeholder="e.g. 250000"
                    />
                    {fieldErr("valueInr")}
                  </div>
                  <div className="fld">
                    <label htmlFor="f-owner">Owner</label>
                    <select
                      id="f-owner"
                      className="field"
                      value={form.ownerId}
                      onChange={(e) => set("ownerId", e.target.value)}
                    >
                      <option value="">Unassigned</option>
                      {users?.items.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name}
                          {u.id === user?.id ? " (you)" : ""}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="fld">
                    <label htmlFor="f-source">Source</label>
                    <select
                      id="f-source"
                      className="field"
                      value={form.source}
                      onChange={(e) => set("source", e.target.value as Form["source"])}
                    >
                      {SOURCES.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="fld">
                    <label htmlFor="f-follow">Next follow-up</label>
                    <input
                      id="f-follow"
                      className="field"
                      type="date"
                      value={form.nextFollowUp}
                      onChange={(e) => set("nextFollowUp", e.target.value)}
                    />
                  </div>
                  {form.stage === "LOST" && (
                    <div className="fld">
                      <label htmlFor="f-lost">Reason lost</label>
                      <input
                        id="f-lost"
                        className="field"
                        value={form.lostReason}
                        onChange={(e) => set("lostReason", e.target.value)}
                        placeholder="Budget, timing, competitor…"
                      />
                    </div>
                  )}
                </div>
                <div className="fld mt">
                  <label htmlFor="f-notes">Notes</label>
                  <textarea
                    id="f-notes"
                    className="field"
                    rows={3}
                    value={form.notes}
                    onChange={(e) => set("notes", e.target.value)}
                  />
                </div>
              </fieldset>
              {canWrite && (
                <div className="d-foot mt">
                  <button
                    className="btn primary"
                    type="submit"
                    disabled={create.isPending || update.isPending}
                  >
                    {isNew ? "Add lead" : "Save changes"}
                  </button>
                  {lead && (
                    <span className="hint">
                      Added {formatDateTime(lead.createdAt)}
                      {lead.createdBy ? ` by ${lead.createdBy.name}` : ""}
                    </span>
                  )}
                  <span className="sp" />
                  {canDelete && !confirmDelete && (
                    <button type="button" className="btn danger" onClick={() => setConfirmDelete(true)}>
                      Delete
                    </button>
                  )}
                </div>
              )}
              {confirmDelete && (
                <div className="confirm mt">
                  <span>Delete this lead and its activity history? This can't be undone.</span>
                  <button type="button" className="btn sm" onClick={() => setConfirmDelete(false)}>
                    Keep
                  </button>
                  <button
                    type="button"
                    className="btn sm danger solid"
                    onClick={() => void onDelete()}
                    disabled={del.isPending}
                  >
                    Delete lead
                  </button>
                </div>
              )}
            </form>
          )}

          {lead && (lead.phone || lead.email) && (
            <section>
              <h3 className="sec-h">Contact</h3>
              <div className="contact">
                {lead.phone && (
                  <CopyChip label="phone" value={lead.phone} href={`tel:${lead.phone.replace(/\s/g, "")}`} />
                )}
                {lead.email && <CopyChip label="email" value={lead.email} href={`mailto:${lead.email}`} />}
              </div>
            </section>
          )}

          {lead && canWrite && (
            <ActivityComposer
              lead={lead}
              onLogged={(next) => setForm((f) => (f ? { ...f, nextFollowUp: next ?? "" } : f))}
            />
          )}
          {lead && <Timeline leadId={lead.id} />}
        </div>
      </aside>
    </>
  );
}

function CopyChip({ label, value, href }: { label: string; value: string; href: string }) {
  const toast = useToast();
  return (
    <span className="copy-chip">
      <a href={href} className="copy">
        {value}
      </a>
      <button
        type="button"
        className="btn sm"
        onClick={() =>
          navigator.clipboard?.writeText(value).then(
            () => toast("Copied"),
            () => toast("Select the text and copy it manually."),
          )
        }
      >
        Copy {label}
      </button>
    </span>
  );
}

function ActivityComposer({ lead, onLogged }: { lead: Lead; onLogged: (next: string | null) => void }) {
  const [type, setType] = useState<ActivityType>("CALL");
  const [body, setBody] = useState("");
  const [next, setNext] = useState(lead.nextFollowUp ?? "");
  const log = useLogActivity(lead.id);
  const toast = useToast();
  const label = ACTIVITY_META[type].label;

  async function submit() {
    if (!body.trim()) {
      toast("Write a short note about the activity first.", { error: true });
      return;
    }
    try {
      const nextValue = next || null;
      await log.mutateAsync({
        type,
        body: body.trim(),
        ...(nextValue !== lead.nextFollowUp ? { nextFollowUp: nextValue } : {}),
      });
      setBody("");
      onLogged(nextValue);
      toast(`${label} logged`);
    } catch (e) {
      toast(errorText(e), { error: true });
    }
  }

  return (
    <section>
      <h3 className="sec-h">Log activity</h3>
      <div className="composer">
        <div className="types" role="group" aria-label="Activity type">
          {LOGGABLE_ACTIVITIES.map((a) => (
            <button key={a.id} type="button" aria-pressed={type === a.id} onClick={() => setType(a.id)}>
              {a.label}
            </button>
          ))}
        </div>
        <textarea
          id="act-body"
          className="field"
          rows={2}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="What happened? e.g. Spoke to CFO, wants a demo next week"
        />
        <div className="row">
          <label className="hint" htmlFor="act-next">
            Next follow-up
          </label>
          <input
            id="act-next"
            className="field"
            type="date"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <span className="sp" />
          <button
            type="button"
            className="btn primary sm"
            onClick={() => void submit()}
            disabled={log.isPending}
          >
            Log {label.toLowerCase()}
          </button>
        </div>
      </div>
    </section>
  );
}

function Timeline({ leadId }: { leadId: string }) {
  const { data, isLoading } = useActivities(leadId);
  return (
    <section>
      <h3 className="sec-h">History</h3>
      <ul className="timeline">
        {isLoading && <li className="muted">Loading…</li>}
        {data?.items.length === 0 && <li className="muted">No activity yet.</li>}
        {data?.items.map((a) => (
          <li key={a.id}>
            <span className={`ic${a.type === "STAGE_CHANGE" ? " stage" : ""}`}>
              {ACTIVITY_META[a.type].icon}
            </span>
            <div>
              <div className="tx">{a.body}</div>
              <div className="meta">
                {ACTIVITY_META[a.type].label} · {formatDateTime(a.createdAt)}
                {a.user ? ` · ${a.user.name}` : ""}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
