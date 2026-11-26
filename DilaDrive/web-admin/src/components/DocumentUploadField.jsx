import { useEffect, useState } from "react";
import { FileText, ImagePlus, UploadCloud, X } from "lucide-react";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const ALLOWED_EXTENSIONS = /\.(jpe?g|png|pdf)$/i;

export default function DocumentUploadField({
  label,
  file,
  onChange,
  currentDocument,
  progress,
  error,
  required = false,
  disabled = false,
}) {
  const [localError, setLocalError] = useState("");
  const [previewUrl, setPreviewUrl] = useState("");

  useEffect(() => {
    if (!file || !file.type.startsWith("image/")) {
      setPreviewUrl("");
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const selectFile = (selected) => {
    setLocalError("");
    if (!selected) return;
    if (!ALLOWED_EXTENSIONS.test(selected.name)) {
      setLocalError("Choose a JPG, PNG, or PDF file.");
      return;
    }
    if (selected.size > MAX_FILE_BYTES) {
      setLocalError("Each document must be no larger than 5 MB.");
      return;
    }
    onChange(selected);
  };

  const activeError = localError || error;
  const displayedFile = file || currentDocument;
  const size = file?.size ?? currentDocument?.size;

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-slate-700">
        {label}{required && <span className="ml-1 text-red-500">*</span>}
      </p>
      <label
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          if (!disabled) selectFile(event.dataTransfer.files?.[0]);
        }}
        className={`relative flex min-h-24 cursor-pointer items-center gap-3 rounded-xl border border-dashed px-4 py-3 transition focus-within:ring-2 focus-within:ring-teal-500 ${
          disabled ? "cursor-not-allowed bg-slate-50 opacity-60" : "border-slate-300 hover:border-teal-500 hover:bg-teal-50/40"
        }`}
      >
        <input
          type="file"
          accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
          className="sr-only left-4 top-1/2"
          aria-label={label}
          aria-invalid={Boolean(activeError)}
          disabled={disabled}
          onChange={(event) => {
            selectFile(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
        <UploadCloud size={20} className="shrink-0 text-teal-700" />
        <span className="min-w-0 flex-1 text-xs text-slate-600">
          <span className="block font-semibold text-slate-800">
            {displayedFile?.name || "Click to upload or drag a file here"}
          </span>
          <span className="mt-1 block">
            JPG, PNG, or PDF · maximum 5 MB
            {size != null && ` · ${(size / (1024 * 1024)).toFixed(2)} MB`}
          </span>
        </span>
        {file && (
          <button
            type="button"
            aria-label={`Remove selected ${label.toLowerCase()}`}
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onChange(null);
              setLocalError("");
            }}
            className="rounded-lg p-2 text-slate-500 hover:bg-white hover:text-red-600"
          >
            <X size={16} />
          </button>
        )}
      </label>
      {previewUrl && (
        <div className="flex items-center gap-3 rounded-lg bg-slate-50 p-2">
          <img src={previewUrl} alt={`Preview of ${label}`} className="h-14 w-14 rounded-md object-cover" />
          <span className="inline-flex items-center gap-1 text-xs text-slate-600">
            <ImagePlus size={14} /> Image preview
          </span>
        </div>
      )}
      {displayedFile && !previewUrl && (
        <p className="flex items-center gap-1 text-xs text-slate-500">
          <FileText size={13} /> {file ? "Selected document" : "Current document"}
        </p>
      )}
      {progress != null && progress >= 0 && (
        <div role="status" className="space-y-1">
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full bg-teal-600 transition-all" style={{ width: `${progress}%` }} />
          </div>
          <p className="text-right text-xs text-slate-500">Uploading {progress}%</p>
        </div>
      )}
      {activeError && <p role="alert" className="text-xs text-red-600">{activeError}</p>}
    </div>
  );
}
