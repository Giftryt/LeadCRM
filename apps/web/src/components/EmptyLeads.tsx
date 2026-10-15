import { useAuth } from "../auth/AuthContext";
import { useFilters } from "../lib/filters";

export function EmptyLeads({ filtered }: { filtered: boolean }) {
  const { canWrite } = useAuth();
  const { openLead } = useFilters();
  if (filtered) {
    return (
      <div className="empty">
        <h2>No leads match these filters</h2>
        <p>Clear the search or change the owner and source filters to see more.</p>
      </div>
    );
  }
  return (
    <div className="empty">
      <h2>No leads yet</h2>
      <p>
        Add your first lead to start the pipeline, or import a CSV with columns like Name, Company, Email,
        Phone, Source, Stage, Value and Next follow-up.
      </p>
      {canWrite && (
        <div className="acts">
          <button className="btn primary" onClick={() => openLead("new")}>
            + Add first lead
          </button>
          <label className="btn" htmlFor="csv-import">
            Import CSV
          </label>
        </div>
      )}
    </div>
  );
}
