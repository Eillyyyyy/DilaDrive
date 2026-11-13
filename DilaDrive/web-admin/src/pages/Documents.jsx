import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import api from "../services/api";
import {
  AlertCircle,
  CheckCircle2,
  ClipboardList,
  CreditCard,
  Download,
  Eye,
  FileSpreadsheet,
  FileText,
  LoaderCircle,
  Pencil,
  Plus,
  Printer,
  Receipt,
  RefreshCw,
  Search,
  X,
  XCircle,
} from "lucide-react";

const API_URL = "/documents";

const DOCUMENT_TYPES = [
  { value: "QUOTATION", label: "Quotation" },
  { value: "PURCHASE_ORDER", label: "Purchase Order" },
  { value: "INVOICE", label: "Invoice" },
  { value: "RECEIPT", label: "Receipt" },
];

const EMPTY_DASHBOARD = {
  totalQuotations: 0,
  totalPurchaseOrders: 0,
  totalInvoices: 0,
  totalReceipts: 0,
  totalReceiptAmount: 0,
};

const formatCurrency = (value) =>
  new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
  }).format(Number(value) || 0);

const formatDate = (value) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString("en-MY", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      });
};

const errorMessage = (error) =>
  (typeof error.response?.data === "string"
    ? error.response.data
    : error.response?.data?.message) ||
  error.message ||
  "The request could not be completed.";

const normalizedStatus = (value) => (value || "").toUpperCase();

export default function Documents() {
  const [documents, setDocuments] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    let active = true;
    Promise.all([
      api.get("/bookings"),
      api.get("/customers"),
    ])
      .then(([bookingResponse, customerResponse]) => {
        const customers = Array.isArray(customerResponse.data)
          ? customerResponse.data
          : [];
        const rows = Array.isArray(bookingResponse.data)
          ? bookingResponse.data
          : [];
        if (active) {
          setBookings(
            rows.map((booking) => ({
              ...booking,
              custName:
                booking.custName ||
                booking.customerName ||
                customers.find(
                  (customer) =>
                    String(customer.custId) === String(booking.custId)
                )?.custName ||
                "Customer",
            }))
          );
        }
      })
      .catch((requestError) => {
        if (active) setError(errorMessage(requestError));
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    api
      .get(API_URL, { signal: controller.signal })
      .then(({ data }) => {
        const rows = Array.isArray(data) ? data : [];
        setDocuments(rows);
      })
      .catch((requestError) => {
        if (!axios.isCancel(requestError)) setError(errorMessage(requestError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [search, typeFilter, refreshKey]);

  useEffect(() => {
    if (modal?.type !== "preview") return undefined;
    const previewUrl = modal.url;
    return () => URL.revokeObjectURL(previewUrl);
  }, [modal]);

  useEffect(() => {
    let active = true;
    setDashboardLoading(true);
    api
      .get(`${API_URL}/dashboard`)
      .then(({ data }) => {
        if (active) setDashboard({ ...EMPTY_DASHBOARD, ...data });
      })
      .catch((requestError) => {
        if (active) setError(errorMessage(requestError));
      })
      .finally(() => {
        if (active) setDashboardLoading(false);
      });
    return () => {
      active = false;
    };
  }, [refreshKey]);

  const completedBookings = useMemo(
    () =>
      bookings.filter(
        (booking) =>
          normalizedStatus(booking.bookingStatus) === "COMPLETED" &&
          !documents.some(
            (document) =>
              document.documentType === "INVOICE" &&
              Number(document.bookingId) === Number(booking.bookingId)
          )
      ),
    [bookings, documents]
  );
  const visibleDocuments = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return documents.filter((document) => {
      const matchesType =
        typeFilter === "ALL" || document.documentType === typeFilter;
      const matchesSearch =
        !query ||
        document.documentNumber?.toLocaleLowerCase().includes(query) ||
        document.custName?.toLocaleLowerCase().includes(query);
      return matchesType && matchesSearch;
    });
  }, [documents, search, typeFilter]);

  const mutate = async (operation, successMessage) => {
    setBusy(true);
    setError("");
    try {
      await operation();
      setNotice(successMessage);
      setModal(null);
      refresh();
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  };

  const handleCreateQuotation = async (event) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    await mutate(
      () =>
        api.post(`${API_URL}/quotation`, {
          bookingId: Number(values.get("bookingId")),
          amount: Number(values.get("amount")),
          remarks: String(values.get("remarks") || "").trim(),
        }),
      "Quotation created successfully."
    );
  };

  const handleUpdateQuotation = async (event) => {
    event.preventDefault();
    const document = modal.document;
    const values = new FormData(event.currentTarget);
    await mutate(
      () =>
        api.put(`${API_URL}/${document.documentId}`, {
          bookingId: Number(values.get("bookingId")),
          amount: Number(values.get("amount")),
          remarks: String(values.get("remarks") || "").trim(),
        }),
      `${document.documentNumber} updated successfully.`
    );
  };

  const runDocumentAction = (url, method, successMessage, body) =>
    mutate(() => api({ url, method, data: body }), successMessage);

  const generateInvoice = (id, sourceType, label) =>
    runDocumentAction(
      `${API_URL}/${id}/generate-invoice`,
      "post",
      `Invoice generated from ${label}.`,
      { sourceType }
    );

  const downloadPdf = async (document) => {
    setError("");
    try {
      const { data } = await api.get(
        `${API_URL}/${document.documentId}/pdf`,
        { params: { download: true }, responseType: "blob" }
      );
      const url = URL.createObjectURL(
        new Blob([data], { type: "application/pdf" })
      );
      const anchor = window.document.createElement("a");
      anchor.href = url;
      anchor.download = `${document.documentNumber}.pdf`;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  };

  const printPdf = async (document) => {
    setError("");
    const printWindow = window.open("about:blank", "_blank");
    if (!printWindow) {
      setError("Allow pop-ups to open and print the PDF.");
      return;
    }
    try {
      const { data } = await api.get(
        `${API_URL}/${document.documentId}/pdf`,
        { params: { download: false }, responseType: "blob" }
      );
      const url = URL.createObjectURL(
        new Blob([data], { type: "application/pdf" })
      );
      printWindow.location.href = url;
      printWindow.focus();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (requestError) {
      printWindow.close();
      setError(errorMessage(requestError));
    }
  };

  const previewPdf = async (document) => {
    setError("");
    try {
      const { data } = await api.get(
        `${API_URL}/${document.documentId}/pdf`,
        { params: { download: false }, responseType: "blob" }
      );
      const url = URL.createObjectURL(
        new Blob([data], { type: "application/pdf" })
      );
      setModal({ type: "preview", document, url });
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  };

  return (
    <main className="admin-page">
      <header className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
            <span>Management</span>
            <span>/</span>
            <span className="font-medium text-blue-600">Documents</span>
          </div>
          <h1 className="admin-page-title">
            Document Management
          </h1>
          <p className="admin-page-description">
            Manage quotations and generate documents through the booking workflow.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setModal({ type: "quotation" })}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
        >
          <Plus size={18} />
          Create Quotation
        </button>
      </header>

      {notice && (
        <div
          role="status"
          className="flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
        >
          <span className="flex items-center gap-2">
            <CheckCircle2 size={17} />
            {notice}
          </span>
          <button type="button" aria-label="Dismiss message" onClick={() => setNotice("")}>
            <X size={16} />
          </button>
        </div>
      )}
      {error && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
        >
          <AlertCircle size={17} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <StatCard title="Total Quotations" value={dashboard.totalQuotations} icon={FileText} loading={dashboardLoading} />
        <StatCard title="Total Purchase Orders" value={dashboard.totalPurchaseOrders} icon={ClipboardList} loading={dashboardLoading} />
        <StatCard title="Total Invoices" value={dashboard.totalInvoices} icon={FileSpreadsheet} loading={dashboardLoading} />
        <StatCard title="Total Receipts" value={dashboard.totalReceipts} icon={Receipt} loading={dashboardLoading} />
        <StatCard title="Receipt Amount" value={formatCurrency(dashboard.totalReceiptAmount)} icon={CreditCard} loading={dashboardLoading} />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="font-semibold text-slate-900">Documents</h2>
            <p className="mt-1 text-xs text-slate-500">
              Search by document number or customer name.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <label className="relative block">
              <Search size={17} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search documents..."
                className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 sm:w-64"
              />
            </label>
            <select
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value)}
              aria-label="Filter by document type"
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700"
            >
              <option value="ALL">All types</option>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </select>
            <button
              type="button"
              onClick={refresh}
              title="Refresh documents"
              className="rounded-xl border border-slate-200 p-2.5 text-slate-500 hover:bg-slate-50"
            >
              <RefreshCw size={17} />
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-100">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">No.</th>
                <th className="px-5 py-3">Document</th>
                <th className="px-5 py-3">Customer / Booking</th>
                <th className="px-5 py-3">Date</th>
                <th className="px-5 py-3 text-right">Amount</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan="7" className="px-5 py-12 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={17} />Loading documents...</td></tr>
              ) : visibleDocuments.length === 0 ? (
                <tr><td colSpan="7" className="px-5 py-12 text-center text-sm text-slate-500">No documents found.</td></tr>
              ) : visibleDocuments.map((document, index) => {
                const status = normalizedStatus(document.status);
                const purchaseOrderHasInvoice = documents.some(
                  (row) => row.documentType === "INVOICE" &&
                    Number(row.sourceDocumentId) === Number(document.documentId)
                );
                const invoiceHasReceipt = documents.some(
                  (row) => row.documentType === "RECEIPT" &&
                    Number(row.sourceDocumentId) === Number(document.documentId)
                );
                return (
                  <tr key={document.documentId} className="hover:bg-slate-50/70">
                    <td className="px-5 py-4 text-sm font-medium text-slate-500">{index + 1}</td>
                    <td className="px-5 py-4">
                      <p className="font-semibold text-slate-900">{document.documentNumber}</p>
                      <p className="mt-1 text-xs text-slate-500">{typeLabel(document.documentType)}</p>
                    </td>
                    <td className="px-5 py-4 text-sm">
                      <p className="font-medium text-slate-800">{document.custName}</p>
                      <p className="mt-1 text-xs text-slate-500">{document.custEmail || document.custPhone || "Customer account"}</p>
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-500">{formatDate(document.createdAt)}</td>
                    <td className="px-5 py-4 text-right text-sm font-semibold text-slate-800">{formatCurrency(document.amount)}</td>
                    <td className="px-5 py-4"><StatusBadge status={status} /></td>
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap justify-end gap-1">
                        <IconButton label={`View ${document.documentNumber}`} onClick={() => setModal({ type: "view", document })} icon={Eye} />
                        {document.documentType === "QUOTATION" && ["DRAFT", "REJECTED"].includes(status) && (
                          <IconButton label={`Edit ${document.documentNumber}`} onClick={() => setModal({ type: "edit", document })} icon={Pencil} />
                        )}
                        {document.documentType === "QUOTATION" && status === "DRAFT" && (
                          <>
                            <IconButton label={`Approve ${document.documentNumber}`} onClick={() => runDocumentAction(`${API_URL}/${document.documentId}/approve`, "put", `${document.documentNumber} approved.`)} icon={CheckCircle2} tone="green" />
                            <IconButton label={`Reject ${document.documentNumber}`} onClick={() => runDocumentAction(`${API_URL}/${document.documentId}/reject`, "put", `${document.documentNumber} rejected.`)} icon={XCircle} tone="red" />
                          </>
                        )}
                        {document.documentType === "QUOTATION" && status === "APPROVED" && (
                          <ActionButton
                            label="Generate PO"
                            onClick={() => runDocumentAction(`${API_URL}/${document.documentId}/generate-po`, "post", `Purchase order generated from ${document.documentNumber}.`)}
                          />
                        )}
                        {document.documentType === "PURCHASE_ORDER" && !purchaseOrderHasInvoice && (
                          <ActionButton label="Generate Invoice" onClick={() => generateInvoice(document.documentId, "PURCHASE_ORDER", document.documentNumber)} />
                        )}
                        {document.documentType === "INVOICE" && status === "ISSUED" && (
                          <ActionButton
                            label="Mark Paid"
                            onClick={() => {
                              if (window.confirm(`Mark invoice ${document.documentNumber} as paid?`)) {
                                runDocumentAction(`${API_URL}/${document.documentId}/mark-paid`, "put", `${document.documentNumber} marked paid.`);
                              }
                            }}
                          />
                        )}
                        {document.documentType === "INVOICE" && status === "PAID" && !invoiceHasReceipt && (
                          <ActionButton label="Generate Receipt" onClick={() => runDocumentAction(`${API_URL}/${document.documentId}/generate-receipt`, "post", `Receipt generated for ${document.documentNumber}.`)} />
                        )}
                        <IconButton label={`Preview ${document.documentNumber} PDF`} onClick={() => previewPdf(document)} icon={FileText} />
                        <IconButton label={`Download ${document.documentNumber} PDF`} onClick={() => downloadPdf(document)} icon={Download} />
                        <IconButton label={`Print ${document.documentNumber}`} onClick={() => printPdf(document)} icon={Printer} />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
          {!loading && `${visibleDocuments.length} document${visibleDocuments.length === 1 ? "" : "s"}`}
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4">
          <h2 className="font-semibold text-slate-900">Completed bookings ready to invoice</h2>
          <p className="mt-1 text-xs text-slate-500">Invoices can also be generated directly from a completed booking.</p>
        </div>
        {completedBookings.length === 0 ? (
          <p className="text-sm text-slate-500">No completed bookings without an invoice.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {completedBookings.map((booking) => (
              <div key={booking.bookingId} className="flex flex-col justify-between gap-3 py-3 sm:flex-row sm:items-center">
                <div>
                  <p className="text-sm font-semibold text-slate-800">{booking.custName || booking.customerName || "Customer booking"}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {booking.bookingTripdate ? formatDate(booking.bookingTripdate) : "Trip date unavailable"}
                    {booking.bookingTripfare != null && ` · ${formatCurrency(booking.bookingTripfare)}`}
                  </p>
                </div>
                <ActionButton
                  label="Generate Invoice"
                  onClick={() => generateInvoice(booking.bookingId, "BOOKING", `booking for ${booking.custName || booking.customerName || "customer"}`)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {modal?.type === "quotation" && (
        <QuotationModal
          title="Create Quotation"
          bookings={bookings}
          busy={busy}
          onClose={() => setModal(null)}
          onSubmit={handleCreateQuotation}
        />
      )}
      {modal?.type === "edit" && (
        <QuotationModal
          title={`Edit ${modal.document.documentNumber}`}
          document={modal.document}
          bookings={bookings}
          busy={busy}
          onClose={() => setModal(null)}
          onSubmit={handleUpdateQuotation}
        />
      )}
      {modal?.type === "view" && (
        <DocumentModal document={modal.document} onClose={() => setModal(null)} />
      )}
      {modal?.type === "preview" && (
        <PdfPreviewModal
          document={modal.document}
          url={modal.url}
          onClose={() => setModal(null)}
          onDownload={() => downloadPdf(modal.document)}
          onPrint={() => printPdf(modal.document)}
        />
      )}
      {busy && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/20">
          <div className="rounded-xl bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-lg">
            <LoaderCircle size={17} className="mr-2 inline animate-spin" />
            Processing...
          </div>
        </div>
      )}
    </main>
  );
}

function typeLabel(type) {
  return DOCUMENT_TYPES.find((item) => item.value === type)?.label || type;
}

function StatCard({ title, value, icon: Icon, loading }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-slate-500">{title}</p>
          <p className="mt-2 text-xl font-bold tracking-tight text-slate-900">{loading ? "…" : value}</p>
        </div>
        <div className="rounded-xl bg-blue-50 p-3 text-blue-600"><Icon size={20} /></div>
      </div>
    </div>
  );
}

function StatusBadge({ status }) {
  const style = status === "APPROVED" || status === "PAID"
    ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
    : status === "REJECTED"
      ? "bg-rose-50 text-rose-700 ring-rose-200"
      : status === "DRAFT"
        ? "bg-amber-50 text-amber-700 ring-amber-200"
        : "bg-blue-50 text-blue-700 ring-blue-200";
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${style}`}>{status}</span>;
}

function IconButton({ label, onClick, icon: Icon, tone = "slate" }) {
  const colors = {
    slate: "text-slate-500 hover:bg-slate-100 hover:text-slate-800",
    green: "text-emerald-600 hover:bg-emerald-50",
    red: "text-rose-600 hover:bg-rose-50",
  };
  return (
    <button type="button" title={label} aria-label={label} onClick={onClick} className={`rounded-lg p-2 transition ${colors[tone]}`}>
      <Icon size={16} />
    </button>
  );
}

function ActionButton({ label, onClick }) {
  return (
    <button type="button" onClick={onClick} className="rounded-lg border border-blue-200 px-2.5 py-1.5 text-xs font-semibold text-blue-700 hover:bg-blue-50">
      {label}
    </button>
  );
}

const inputClass = "mt-1 block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function QuotationModal({ title, document, bookings, busy, onClose, onSubmit }) {
  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        <label className="block text-sm font-medium text-slate-700">
          Booking
          <select name="bookingId" required defaultValue={document?.bookingId || ""} className={inputClass}>
            <option value="" disabled>Select booking</option>
            {bookings.map((booking) => (
              <option key={booking.bookingId} value={booking.bookingId}>
                {booking.custName || booking.customerName || "Customer"} · {booking.bookingTripdate || "Trip date unavailable"} · {booking.bookingStatus || "Unknown status"}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Amount (MYR)
          <input name="amount" type="number" min="0.01" step="0.01" required defaultValue={document?.amount || ""} className={inputClass} />
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Remarks
          <textarea name="remarks" rows={3} maxLength={5000} defaultValue={document?.remarks || ""} className={inputClass} />
        </label>
        {bookings.length === 0 && <p className="text-sm text-rose-700">No bookings are available. Create a booking before making a quotation.</p>}
        <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
          <button type="button" onClick={onClose} disabled={busy} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">Cancel</button>
          <button type="submit" disabled={busy || bookings.length === 0} className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">
            {busy && <LoaderCircle size={15} className="mr-2 inline animate-spin" />}
            {document ? "Save Changes" : "Create Quotation"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function DocumentModal({ document, onClose }) {
  const rows = [
    ["Type", typeLabel(document.documentType)],
    ["Status", document.status],
    ["Customer", document.custName],
    ["Email", document.custEmail || "—"],
    ["Phone", document.custPhone || "—"],
    ["Amount", formatCurrency(document.amount)],
    ["Created", formatDate(document.createdAt)],
  ];
  return (
    <Modal title={document.documentNumber} onClose={onClose}>
      <div className="grid gap-3 p-5 sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs font-medium text-slate-500">{label}</p>
            <p className="mt-1.5 break-words text-sm font-semibold text-slate-800">{value}</p>
          </div>
        ))}
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 sm:col-span-2">
          <p className="text-xs font-medium text-slate-500">Remarks</p>
          <p className="mt-1.5 whitespace-pre-wrap text-sm text-slate-800">{document.remarks || "—"}</p>
        </div>
      </div>
      <div className="flex justify-end border-t border-slate-100 p-5 pt-4">
        <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600">Close</button>
      </div>
    </Modal>
  );
}

function PdfPreviewModal({ document, url, onClose, onDownload, onPrint }) {
  return (
    <Modal title={`PDF Preview · ${document.documentNumber}`} onClose={onClose} className="max-w-5xl">
      <iframe
        title={`${document.documentNumber} PDF preview`}
        src={url}
        className="h-[72vh] w-full bg-slate-100"
      />
      <div className="flex justify-end gap-2 border-t border-slate-100 p-4">
        <button type="button" onClick={onPrint} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600">Print PDF</button>
        <button type="button" onClick={onDownload} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white">Download PDF</button>
      </div>
    </Modal>
  );
}

function Modal({ title, onClose, children, className = "max-w-xl" }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-label={title} className={`my-auto w-full overflow-hidden rounded-2xl bg-white shadow-2xl ${className}`}>
        <header className="flex items-start justify-between border-b border-slate-100 p-5">
          <h2 className="font-semibold text-slate-900">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100"><X size={18} /></button>
        </header>
        {children}
      </section>
    </div>
  );
}
