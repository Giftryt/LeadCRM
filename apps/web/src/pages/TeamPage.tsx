import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api, type User } from "../api/client";
import { useUsers } from "../api/hooks";
import { useAuth } from "../auth/AuthContext";
import { errorText, useToast } from "../components/Toast";
import { ROLES, type Role } from "../lib/constants";

export function TeamPage() {
  const { data, isLoading } = useUsers(true);
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);

  async function patch(u: User, body: Partial<{ role: Role; active: boolean }>) {
    setBusy(u.id);
    try {
      await api.updateUser(u.id, body);
      await qc.invalidateQueries({ queryKey: ["users"] });
      toast("Saved");
    } catch (e) {
      toast(errorText(e), { error: true });
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="stack">
      <div>
        <h2 className="page-h">Team</h2>
        <p className="muted">Admins manage users. Sales reps add and work leads. Viewers can only read.</p>
      </div>
      <AddUser />
      <div className="tbl-wrap">
        <table className="narrow">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={4} className="muted">
                  Loading…
                </td>
              </tr>
            )}
            {data?.items.map((u) => (
              <tr key={u.id} className={u.active ? undefined : "inactive"}>
                <td>
                  <b>{u.name}</b>
                  {u.id === me?.id && <span className="muted"> (you)</span>}
                </td>
                <td className="muted">{u.email}</td>
                <td>
                  <select
                    id={`role-${u.id}`}
                    className="field"
                    aria-label={`Role for ${u.name}`}
                    value={u.role}
                    disabled={busy === u.id || u.id === me?.id}
                    onChange={(e) => void patch(u, { role: e.target.value as Role })}
                  >
                    {ROLES.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  {u.id === me?.id ? (
                    <span className="pill">Active</span>
                  ) : (
                    <button
                      className="btn sm"
                      disabled={busy === u.id}
                      onClick={() => void patch(u, { active: !u.active })}
                    >
                      {u.active ? "Deactivate" : "Reactivate"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function AddUser() {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ name: "", email: "", role: "REP" as Role, password: "" });
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      await api.createUser(form);
      await qc.invalidateQueries({ queryKey: ["users"] });
      toast(`${form.name} added. Share the temporary password with them privately.`);
      setForm({ name: "", email: "", role: "REP", password: "" });
    } catch (err) {
      toast(errorText(err), { error: true });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card-box" onSubmit={submit}>
      <h3 className="sec-h">Add a teammate</h3>
      <div className="grid4">
        <div className="fld">
          <label htmlFor="nu-name">Name</label>
          <input
            id="nu-name"
            className="field"
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </div>
        <div className="fld">
          <label htmlFor="nu-email">Email</label>
          <input
            id="nu-email"
            className="field"
            type="email"
            required
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </div>
        <div className="fld">
          <label htmlFor="nu-role">Role</label>
          <select
            id="nu-role"
            className="field"
            value={form.role}
            onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
          >
            {ROLES.map((r) => (
              <option key={r.id} value={r.id}>
                {r.label}
              </option>
            ))}
          </select>
        </div>
        <div className="fld">
          <label htmlFor="nu-pass">Temporary password</label>
          <input
            id="nu-pass"
            className="field"
            type="text"
            required
            minLength={10}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            placeholder="10+ characters, letters and numbers"
          />
        </div>
      </div>
      <div className="mt">
        <button className="btn primary" type="submit" disabled={saving}>
          {saving ? "Adding…" : "Add teammate"}
        </button>
      </div>
    </form>
  );
}
