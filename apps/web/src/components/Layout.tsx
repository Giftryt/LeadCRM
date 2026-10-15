import { useRef, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { api } from "../api/client";
import { useImportLeads, useSummary, useUsers } from "../api/hooks";
import { useAuth } from "../auth/AuthContext";
import { SOURCES, STAGES } from "../lib/constants";
import { useFilters } from "../lib/filters";
import { inrShort } from "../lib/format";
import { LeadDrawer } from "./LeadDrawer";
import { errorText, useToast } from "./Toast";

export function Layout() {
  const { user, canWrite, isAdmin, logout } = useAuth();
  const { pathname } = useLocation();
  const leadViews = ["/", "/leads", "/follow-ups"].includes(pathname);

  return (
    <>
      <header className="top">
        <div className="wrap top-row">
          <div className="brand">
            <span className="logo" aria-hidden="true" />
            <h1>Lead Tracker</h1>
          </div>
          <nav className="tabs" aria-label="Main">
            <NavLink to="/" end className="tab">
              Pipeline
            </NavLink>
            <NavLink to="/leads" className="tab">
              All leads
            </NavLink>
            <NavLink to="/follow-ups" className="tab">
              Follow-ups
              <DueBadge />
            </NavLink>
            {isAdmin && (
              <NavLink to="/team" className="tab">
                Team
              </NavLink>
            )}
          </nav>
          <div className="top-actions">
            {leadViews && <LeadActions canWrite={canWrite} />}
            <details className="menu">
              <summary className="btn ghost" aria-label="Account menu">
                <span className="av">{user?.name.slice(0, 1).toUpperCase()}</span>
                <span className="hide-sm">{user?.name}</span>
              </summary>
              <div className="menu-pop">
                <div className="menu-head">
                  <b>{user?.name}</b>
                  <span className="muted">{user?.email}</span>
                </div>
                <NavLink to="/account" className="menu-item">
                  Account & password
                </NavLink>
                <button className="menu-item" onClick={() => void logout()}>
                  Sign out
                </button>
              </div>
            </details>
          </div>
        </div>
      </header>
      <div className="wrap">
        {leadViews && (
          <>
            <Metrics />
            <Filters />
          </>
        )}
      </div>
      <main className="wrap main">
        <Outlet />
      </main>
      {leadViews && <DrawerHost />}
    </>
  );
}

function DueBadge() {
  const { data } = useSummary();
  const n = (data?.followUps.overdue ?? 0) + (data?.followUps.today ?? 0);
  return n ? (
    <span className="badge" aria-label={`${n} due`}>
      {n}
    </span>
  ) : null;
}

function LeadActions({ canWrite }: { canWrite: boolean }) {
  const { query, openLead } = useFilters();
  const fileRef = useRef<HTMLInputElement>(null);
  const importer = useImportLeads();
  const toast = useToast();
  const [errors, setErrors] = useState<{ row: number; message: string }[] | null>(null);

  async function onFile(file: File) {
    try {
      const r = await importer.mutateAsync(file);
      toast(
        `Imported ${r.imported} lead${r.imported === 1 ? "" : "s"}${r.skipped ? `, skipped ${r.skipped}` : ""}`,
      );
      setErrors(r.errors.length ? r.errors : null);
    } catch (e) {
      toast(errorText(e), { error: true });
    }
  }

  return (
    <>
      {canWrite && (
        <>
          <input
            ref={fileRef}
            id="csv-import"
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0];
              e.target.value = "";
              if (f) void onFile(f);
            }}
          />
          <button
            className="btn ghost hide-sm"
            onClick={() => fileRef.current?.click()}
            disabled={importer.isPending}
          >
            {importer.isPending ? "Importing…" : "Import CSV"}
          </button>
        </>
      )}
      <a className="btn ghost hide-sm" href={api.exportUrl(query)} download>
        Export CSV
      </a>
      {canWrite && (
        <button className="btn primary" onClick={() => openLead("new")}>
          + New lead
        </button>
      )}
      {errors && (
        <div className="scrim" onClick={() => setErrors(null)}>
          <div className="dialog" role="dialog" aria-labelledby="impErr" onClick={(e) => e.stopPropagation()}>
            <h2 id="impErr">Some rows were skipped</h2>
            <p className="muted">Fix these rows in your file and import just those rows again.</p>
            <ul className="err-list">
              {errors.map((e) => (
                <li key={e.row}>
                  <b>Row {e.row}</b> {e.message}
                </li>
              ))}
            </ul>
            <button className="btn primary" onClick={() => setErrors(null)}>
              Done
            </button>
          </div>
        </div>
      )}
    </>
  );
}

function Metrics() {
  const { filters } = useFilters();
  const owner = filters.owner === "me" || filters.owner === "none" ? filters.owner : undefined;
  const { data: s } = useSummary(owner);
  const month = new Date().toLocaleDateString("en-IN", { month: "long" });
  const dash = "—";
  return (
    <section className="metrics" aria-label="Pipeline summary">
      <div className="metric">
        <div className="lbl">Open pipeline</div>
        <div className="val">{s ? inrShort(s.openPipeline.valueInr) : dash}</div>
        <div className="sub">
          {s ? `${s.openPipeline.count} open lead${s.openPipeline.count === 1 ? "" : "s"}` : " "}
        </div>
      </div>
      <div className="metric">
        <div className="lbl">Weighted forecast</div>
        <div className="val">{s ? inrShort(s.weightedForecastInr) : dash}</div>
        <div className="sub">Value × stage probability</div>
      </div>
      <div className="metric">
        <div className="lbl">Won in {month}</div>
        <div className="val">{s ? inrShort(s.wonThisMonth.valueInr) : dash}</div>
        <div className="sub">
          {s
            ? `${s.wonThisMonth.count} deal${s.wonThisMonth.count === 1 ? "" : "s"}${
                s.winRate !== null ? ` · ${Math.round(s.winRate * 100)}% win rate overall` : ""
              }`
            : " "}
        </div>
      </div>
      <div className="metric">
        <div className="lbl">Follow-ups</div>
        <div className="val">
          {s ? <span className={s.followUps.overdue ? "warn" : undefined}>{s.followUps.overdue}</span> : dash}{" "}
          <span className="sub">overdue</span>
        </div>
        <div className="sub">{s ? `${s.followUps.today} due today` : " "}</div>
      </div>
    </section>
  );
}

function Filters() {
  const { filters, setFilter } = useFilters();
  const { pathname } = useLocation();
  const { data: users } = useUsers();
  return (
    <div className="filters">
      <input
        id="filter-q"
        className="field search"
        type="search"
        placeholder="Search name, company, email, phone"
        aria-label="Search leads"
        defaultValue={filters.q}
        onChange={(e) => setFilter("q", e.target.value)}
      />
      <select
        id="filter-owner"
        className="field"
        aria-label="Filter by owner"
        value={filters.owner}
        onChange={(e) => setFilter("owner", e.target.value)}
      >
        <option value="all">All owners</option>
        <option value="me">My leads</option>
        <option value="none">Unassigned</option>
        {users?.items.map((u) => (
          <option key={u.id} value={u.id}>
            {u.name}
          </option>
        ))}
      </select>
      <select
        id="filter-source"
        className="field"
        aria-label="Filter by source"
        value={filters.source}
        onChange={(e) => setFilter("source", e.target.value)}
      >
        <option value="all">All sources</option>
        {SOURCES.map((s) => (
          <option key={s.id} value={s.id}>
            {s.label}
          </option>
        ))}
      </select>
      {pathname !== "/" && (
        <select
          id="filter-stage"
          className="field"
          aria-label="Filter by stage"
          value={filters.stage}
          onChange={(e) => setFilter("stage", e.target.value)}
        >
          <option value="all">All stages</option>
          {STAGES.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}

function DrawerHost() {
  const { openLeadId, openLead, closeLead } = useFilters();
  if (!openLeadId) return null;
  return (
    <LeadDrawer
      key={openLeadId}
      leadId={openLeadId === "new" ? null : openLeadId}
      onClose={closeLead}
      onCreated={openLead}
    />
  );
}
