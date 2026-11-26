import { useEffect, useState } from "react";
import { Eye } from "lucide-react";
import api from "../services/api";

export default function SecureDocumentPreview({ documentInfo }) {
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !documentInfo?.id) return undefined;
    const controller = new AbortController();
    let objectUrl;
    setLoading(true);
    setError("");
    api.get(`/fleet-documents/${documentInfo.id}/content`, {
      responseType: "blob",
      signal: controller.signal,
    }).then(({ data }) => {
      if (controller.signal.aborted) return;
      objectUrl = URL.createObjectURL(data);
      setUrl(objectUrl);
    }).catch((requestError) => {
      if (!controller.signal.aborted) {
        setError(requestError.response?.data?.message || "The document could not be loaded.");
      }
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setUrl("");
    };
  }, [open, documentInfo?.id]);

  if (!documentInfo) return <span className="text-xs text-slate-500">Not uploaded</span>;

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-slate-600">{documentInfo.filename}</span>
        <button type="button" onClick={() => setOpen(value => !value)}
          className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs font-medium text-teal-700 hover:bg-teal-50">
          <Eye size={13} /> {open ? "Hide preview" : "Secure preview"}
        </button>
      </div>
      {loading && <p role="status" className="text-xs text-slate-500">Loading secure preview…</p>}
      {error && <p role="alert" className="text-xs text-red-600">{error}</p>}
      {url && documentInfo.contentType?.startsWith("image/") && (
        <img src={url} alt={`Preview of ${documentInfo.filename}`} className="max-h-64 max-w-full rounded-lg border object-contain" />
      )}
      {url && documentInfo.contentType === "application/pdf" && (
        <iframe title={`Preview of ${documentInfo.filename}`} src={url} className="h-72 w-full rounded-lg border" />
      )}
    </div>
  );
}
