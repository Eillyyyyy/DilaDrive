import { useEffect, useState } from "react";
import api from "../services/api";

export default function AssignmentTimeline({ assignmentId }) {
  const [result, setResult] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const request = new AbortController();
    api.get(`/assignments/${assignmentId}/timeline`, { signal: request.signal })
      .then(({ data }) => { if (!request.signal.aborted) setResult({ rows: data }); })
      .catch(() => { if (!request.signal.aborted) setResult({ error: true }); });
    return () => request.abort();
  }, [assignmentId, revision]);
  return <section className="space-y-2 px-5 pb-4">
    <h3 className="font-semibold">Trip timeline</h3>
    {!result && <p role="status">Loading milestones…</p>}
    {result?.error && <button type="button" onClick={() => setRevision(value => value + 1)}>Retry milestones</button>}
    {result?.rows?.length === 0 && <p>No recorded milestones</p>}
    <ol className="space-y-1 text-sm">
      {result?.rows?.map(event => <li key={event.milestone}>
        {event.milestone.replaceAll("_", " ")} · {new Date(event.occurredAt)
          .toLocaleString("en-MY", { timeZone: "Asia/Kuala_Lumpur" })}
      </li>)}
    </ol>
  </section>;
}
