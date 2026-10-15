import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { errorText, useToast } from "../components/Toast";
import { ROLES } from "../lib/constants";

export function AccountPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (next !== confirm) {
      toast("The new passwords don't match.", { error: true });
      return;
    }
    setSaving(true);
    try {
      await api.changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      toast("Password changed");
    } catch (err) {
      toast(errorText(err), { error: true });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="stack narrow-col">
      <div>
        <h2 className="page-h">Account</h2>
        <p className="muted">
          {user?.name} · {user?.email} · {ROLES.find((r) => r.id === user?.role)?.label}
        </p>
      </div>
      <form className="card-box stack" onSubmit={submit}>
        <h3 className="sec-h">Change password</h3>
        <div className="fld">
          <label htmlFor="pw-current">Current password</label>
          <input
            id="pw-current"
            className="field"
            type="password"
            autoComplete="current-password"
            required
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </div>
        <div className="fld">
          <label htmlFor="pw-new">New password</label>
          <input
            id="pw-new"
            className="field"
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <span className="hint">At least 10 characters, with a letter and a number.</span>
        </div>
        <div className="fld">
          <label htmlFor="pw-confirm">Confirm new password</label>
          <input
            id="pw-confirm"
            className="field"
            type="password"
            autoComplete="new-password"
            required
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </div>
        <div>
          <button className="btn primary" type="submit" disabled={saving}>
            {saving ? "Saving…" : "Change password"}
          </button>
        </div>
      </form>
    </div>
  );
}
