import { useEffect, useState } from "react";
import axios from "axios";
import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  CreditCard,
  Eye,
  FileSpreadsheet,
  FileText,
  LoaderCircle,
  Pencil,
  Plus,
  Receipt,
  RefreshCw,
  Search,
  Trash2,
  UserRound,
  X,
  XCircle,
} from "lucide-react";

const API_URL = "http://localhost:8080/api/documents";
const RESOURCE_API_URL = "http://localhost:8080/api";

const DOCUMENT_TYPES = [
  { value: "QUOTATION", label: "Quotation", icon: FileText },
  { value: "PURCHASE_ORDER", label: "Purchase Order", icon: ClipboardList },
  { value: "INVOICE", label: "Invoice", icon: FileSpreadsheet },
  { value: "RECEIPT", label: "Receipt", icon: Receipt },
];

const EMPTY_DASHBOARD = {
  totalQuotations: 0,
  totalPurchaseOrders: 0,
  totalInvoices: 0,
  totalReceipts: 0,
  totalReceiptAmount: 0,
};

const formatCurrency = (amount) =>
  new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
  }).format(Number(amount) || 0);

const formatDate = (date) => {
  if (!date) return "—";
  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
};

const typeLabel = (type) =>
  DOCUMENT_TYPES.find((option) => option.value === type)?.label || type;

const getErrorMessage = (error) => {
  const responseData = error.response?.data;
  return (
    (typeof responseData === "string" ? responseData : responseData?.message) ||
    error.message ||
    "The request could not be completed."
  );
};

const statusClass = (status) => {
  const normalized = (status || "").toUpperCase();
  if (normalized === "APPROVED" || normalized === "PAID") {
    return "bg-emerald-50 text-emerald-700 ring-emerald-200";
  }
  if (normalized === "REJECTED") {
    return "bg-rose-50 text-rose-700 ring-rose-200";
  }
  if (normalized === "DRAFT" || normalized === "PENDING") {
    return "bg-amber-50 text-amber-700 ring-amber-200";
  }
  return "bg-blue-50 text-blue-700 ring-blue-200";
};

export default function Documents() {
  const [documents, setDocuments] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [error, setError] = useState("");
  const [resourceError, setResourceError] = useState("");
  const [notice, setNotice] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    let active = true;

    Promise.all([
      axios.get(`${RESOURCE_API_URL}/customers`),
      axios.get(`${RESOURCE_API_URL}/bookings`),
    ])
      .then(([customerResponse, bookingResponse]) => {
        if (!active) return;
        setCustomers(customerResponse.data);
        setBookings(bookingResponse.data);
        setResourceError("");
      })
      .catch((requestError) => {
        if (active) setResourceError(getErrorMessage(requestError));
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const trimmedSearch = search.trim();
    let request;

    if (trimmedSearch) {
      request = axios.get(`${API_URL}/search`, {
        params: { query: trimmedSearch },
        signal: controller.signal,
      });
    } else if (typeFilter !== "ALL") {
      request = axios.get(`${API_URL}/type/${typeFilter}`, {
        signal: controller.signal,
      });
    } else {
      request = axios.get(API_URL, { signal: controller.signal });
    }

    setLoading(true);
    setError("");
    request
      .then((response) =>
        setDocuments(
          response.data.filter(
            (document) =>
              typeFilter === "ALL" || document.documentType === typeFilter
          )
        )
      )
      .catch((requestError) => {
        if (!axios.isCancel(requestError)) setError(getErrorMessage(requestError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [search, typeFilter, refreshKey]);

  useEffect(() => {
    let active = true;
    setDashboardLoading(true);
    axios
      .get(`${API_URL}/dashboard`)
      .then((response) => {
        if (active) setDashboard({ ...EMPTY_DASHBOARD, ...response.data });
      })
      .catch((requestError) => {
        if (active) setError(getErrorMessage(requestError));
      })
      .finally(() => {
        if (active) setDashboardLoading(false);
      });

    return () => {
      active = false;
    };
  }, [refreshKey]);

  const visibleDocuments = documents;

  const showModal = (nextModal) => {
    setFormError("");
    setModal(nextModal);
  };

  const completeAction = (message) => {
    setNotice(message);
    setError("");
    setFormError("");
    setModal(null);
    setRefreshKey((current) => current + 1);
  };

  const submitMutation = async (operation, successMessage) => {
    setBusy(true);
    setFormError("");
    setError("");
    try {
      await operation();
      completeAction(successMessage);
    } catch (requestError) {
      const message = getErrorMessage(requestError);
      setFormError(message);
      if (!modal) setError(message);
    } finally {
      setBusy(false);
    }
  };

  const handleCreate = (event) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const bookingId = Number(formData.get("bookingId"));
    const booking = bookings.find((item) => Number(item.bookingId) === bookingId);
    if (!booking) {
      setFormError("Select a valid booking.");
      return;
    }

    const amount = Number(formData.get("amount"));
    const remarks = String(formData.get("remarks") || "").trim();
    const documentType = String(formData.get("documentType"));
    const operation =
      documentType === "INVOICE"
        ? () =>
            axios.post(`${API_URL}/invoices`, {
              bookingId,
              amount,
              remarks,
            })
        : () =>
            axios.post(API_URL, {
              documentType: "QUOTATION",
              bookingId,
              custId: booking.custId,
              amount,
              remarks,
            });

    submitMutation(
      operation,
      `${typeLabel(documentType)} created successfully.`
    );
  };

  const handleEdit = (event) => {
    event.preventDefault();
    const document = modal.document;
    const formData = new FormData(event.currentTarget);
    const bookingId = Number(formData.get("bookingId"));
    const booking = bookings.find((item) => Number(item.bookingId) === bookingId);
    if (!booking) {
      setFormError("Select a valid booking.");
      return;
    }

    submitMutation(
      () =>
        axios.put(`${API_URL}/${document.documentId}`, {
          documentType: "QUOTATION",
          bookingId,
          custId: booking.custId,
          amount: Number(formData.get("amount")),
          remarks: String(formData.get("remarks") || "").trim(),
        }),
      `${document.documentNumber} updated successfully.`
    );
  };

  const handleDelete = () =>
    submitMutation(
      () => axios.delete(`${API_URL}/${modal.document.documentId}`),
      `${modal.document.documentNumber} deleted successfully.`
    );

  const handleApprove = (document) =>
    submitMutation(
      () => axios.post(`${API_URL}/${document.documentId}/approve`),
      `${document.documentNumber} approved.`
    );

  const handleReject = (document) => {
    if (!window.confirm(`Reject quotation ${document.documentNumber}?`)) return;
    submitMutation(
      () => axios.post(`${API_URL}/${document.documentId}/reject`),
      `${document.documentNumber} rejected.`
    );
  };

  const handleGeneratePurchaseOrder = (document) =>
    submitMutation(
      () => axios.post(`${API_URL}/${document.documentId}/purchase-order`),
      `Purchase order generated from ${document.documentNumber}.`
    );

  const handleGenerateReceipt = (event) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    submitMutation(
      () =>
        axios.post(`${API_URL}/${modal.document.documentId}/receipt`, {
          paymentConfirmed: formData.get("paymentConfirmed") === "on",
          paymentReference: String(formData.get("paymentReference") || "").trim(),
          paymentMethod: String(formData.get("paymentMethod") || "").trim(),
        }),
      `Receipt generated for ${modal.document.documentNumber}.`
    );
  };

  const clearFilters = () => {
    setSearch("");
    setTypeFilter("ALL");
  };

  return (
    <div className="min-h-screen space-y-6 bg-slate-50/70 p-4 md:p-7">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
            <span>Management</span>
            <span>/</span>
            <span className="font-medium text-blue-600">Documents</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">
            Document Management
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Manage quotations and generate purchase orders, invoices, and receipts.
          </p>
        </div>
        <button
          type="button"
          onClick={() => showModal({ type: "create" })}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
        >
          <Plus size={18} />
          Create Document
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
          <button
            type="button"
            aria-label="Dismiss message"
            onClick={() => setNotice("")}
            className="rounded p-1 hover:bg-emerald-100"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {(error || resourceError) && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
        >
          <AlertCircle size={17} className="mt-0.5 shrink-0" />
          <span>{error || resourceError}</span>
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <StatCard
          title="Total Quotations"
          value={dashboard.totalQuotations}
          icon={FileText}
          color="blue"
          loading={dashboardLoading}
        />
        <StatCard
          title="Total Purchase Orders"
          value={dashboard.totalPurchaseOrders}
          icon={ClipboardList}
          color="orange"
          loading={dashboardLoading}
        />
        <StatCard
          title="Total Invoices"
          value={dashboard.totalInvoices}
          icon={FileSpreadsheet}
          color="green"
          loading={dashboardLoading}
        />
        <StatCard
          title="Total Receipts"
          value={dashboard.totalReceipts}
          icon={Receipt}
          color="purple"
          loading={dashboardLoading}
        />
        <StatCard
          title="Receipt Amount"
          value={formatCurrency(dashboard.totalReceiptAmount)}
          icon={CreditCard}
          color="emerald"
          loading={dashboardLoading}
        />
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
              <Search
                size={17}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
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
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              <option value="ALL">All types</option>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
            <button
              type="button"
              title="Refresh documents"
              onClick={() => setRefreshKey((current) => current + 1)}
              className="inline-flex items-center justify-center rounded-xl border border-slate-200 px-3 py-2.5 text-slate-600 hover:bg-slate-50"
            >
              <RefreshCw size={17} />
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[880px] text-left">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-semibold">Document</th>
                <th className="px-5 py-3 font-semibold">Customer</th>
                <th className="px-5 py-3 font-semibold">Booking</th>
                <th className="px-5 py-3 font-semibold">Date</th>
                <th className="px-5 py-3 text-right font-semibold">Amount</th>
                <th className="px-5 py-3 font-semibold">Status</th>
                <th className="px-5 py-3 text-right font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-14 text-center text-sm text-slate-500">
                    <LoaderCircle size={22} className="mx-auto animate-spin text-blue-600" />
                    <span className="mt-2 block">Loading documents from PostgreSQL...</span>
                  </td>
                </tr>
              ) : visibleDocuments.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-14 text-center">
                    <FileText size={28} className="mx-auto text-slate-300" />
                    <p className="mt-3 font-semibold text-slate-800">No documents found</p>
                    <p className="mt-1 text-sm text-slate-500">
                      Create a quotation or change the search filters.
                    </p>
                    {(search || typeFilter !== "ALL") && (
                      <button
                        type="button"
                        onClick={clearFilters}
                        className="mt-3 text-sm font-semibold text-blue-600 hover:text-blue-700"
                      >
                        Clear filters
                      </button>
                    )}
                  </td>
                </tr>
              ) : (
                visibleDocuments.map((document) => (
                  <tr key={document.documentId} className="hover:bg-slate-50/70">
                    <td className="px-5 py-4">
                      <p className="font-semibold text-slate-900">
                        {document.documentNumber}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {typeLabel(document.documentType)}
                      </p>
                    </td>
                    <td className="px-5 py-4">
                      <p className="text-sm font-medium text-slate-800">
                        {document.custName}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {document.custEmail || document.custPhone || "—"}
                      </p>
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-600">
                      #{document.bookingId}
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-500">
                      {formatDate(document.createdAt)}
                    </td>
                    <td className="px-5 py-4 text-right text-sm font-semibold text-slate-800">
                      {formatCurrency(document.amount)}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClass(
                          document.status
                        )}`}
                      >
                        {document.status}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-1.5">
                        <IconButton
                          label={`View ${document.documentNumber}`}
                          onClick={() => showModal({ type: "view", document })}
                          icon={Eye}
                        />
                        {document.documentType === "QUOTATION" &&
                          ["DRAFT", "REJECTED"].includes(
                            (document.status || "").toUpperCase()
                          ) && (
                            <>
                              <IconButton
                                label={`Edit ${document.documentNumber}`}
                                onClick={() => showModal({ type: "edit", document })}
                                icon={Pencil}
                              />
                              {(document.status || "").toUpperCase() === "DRAFT" && (
                                <>
                                  <IconButton
                                    label={`Approve ${document.documentNumber}`}
                                    onClick={() => handleApprove(document)}
                                    icon={CheckCircle2}
                                    tone="green"
                                  />
                                  <IconButton
                                    label={`Reject ${document.documentNumber}`}
                                    onClick={() => handleReject(document)}
                                    icon={XCircle}
                                    tone="red"
                                  />
                                </>
                              )}
                            </>
                          )}
                        {document.documentType === "QUOTATION" &&
                          (document.status || "").toUpperCase() === "APPROVED" && (
                            <button
                              type="button"
                              onClick={() => handleGeneratePurchaseOrder(document)}
                              className="rounded-lg border border-orange-200 px-2.5 py-1.5 text-xs font-semibold text-orange-700 hover:bg-orange-50"
                            >
                              Generate PO
                            </button>
                          )}
                        {document.documentType === "INVOICE" &&
                          (document.status || "").toUpperCase() === "ISSUED" && (
                            <button
                              type="button"
                              onClick={() =>
                                showModal({ type: "receipt", document })
                              }
                              className="rounded-lg border border-emerald-200 px-2.5 py-1.5 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
                            >
                              Generate Receipt
                            </button>
                          )}
                        <IconButton
                          label={`Delete ${document.documentNumber}`}
                          onClick={() => showModal({ type: "delete", document })}
                          icon={Trash2}
                          tone="red"
                        />
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
          {loading
            ? " "
            : `${visibleDocuments.length} document${
                visibleDocuments.length === 1 ? "" : "s"
              }`}
        </div>
      </section>

      {modal?.type === "create" && (
        <DocumentFormModal
          title="Create Document"
          bookings={bookings}
          customers={customers}
          busy={busy}
          error={formError}
          onClose={() => setModal(null)}
          onSubmit={handleCreate}
        />
      )}
      {modal?.type === "edit" && (
        <DocumentFormModal
          title={`Edit ${modal.document.documentNumber}`}
          document={modal.document}
          bookings={bookings}
          customers={customers}
          busy={busy}
          error={formError}
          onClose={() => setModal(null)}
          onSubmit={handleEdit}
        />
      )}
      {modal?.type === "view" && (
        <DocumentDetailsModal
          document={modal.document}
          onClose={() => setModal(null)}
        />
      )}
      {modal?.type === "delete" && (
        <ConfirmModal
          title="Delete document?"
          message={`This will permanently delete ${modal.document.documentNumber}. Related financial records may prevent deletion.`}
          busy={busy}
          error={formError}
          onClose={() => setModal(null)}
          onConfirm={handleDelete}
        />
      )}
      {modal?.type === "receipt" && (
        <ReceiptModal
          document={modal.document}
          busy={busy}
          error={formError}
          onClose={() => setModal(null)}
          onSubmit={handleGenerateReceipt}
        />
      )}
    </div>
  );
}

function StatCard({ title, value, icon: Icon, color, loading }) {
  const colors = {
    blue: "bg-blue-50 text-blue-600",
    orange: "bg-orange-50 text-orange-600",
    green: "bg-green-50 text-green-600",
    purple: "bg-purple-50 text-purple-600",
    emerald: "bg-emerald-50 text-emerald-600",
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-slate-500">{title}</p>
          <p className="mt-2 text-xl font-bold tracking-tight text-slate-900">
            {loading ? "…" : value}
          </p>
        </div>
        <div className={`rounded-xl p-3 ${colors[color]}`}>
          <Icon size={20} />
        </div>
      </div>
    </div>
  );
}

function IconButton({ label, onClick, icon: Icon, tone = "slate" }) {
  const tones = {
    slate: "text-slate-500 hover:bg-slate-100 hover:text-slate-800",
    green: "text-emerald-600 hover:bg-emerald-50",
    red: "text-rose-600 hover:bg-rose-50",
  };

  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className={`rounded-lg p-2 transition ${tones[tone]}`}
    >
      <Icon size={16} />
    </button>
  );
}

function Modal({ title, subtitle, onClose, children, maxWidth = "max-w-xl" }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`my-auto w-full ${maxWidth} overflow-hidden rounded-2xl bg-white shadow-2xl`}
      >
        <header className="flex items-start justify-between border-b border-slate-100 p-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">{title}</h2>
            {subtitle && (
              <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
          >
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

function DocumentFormModal({
  title,
  document,
  bookings,
  customers,
  busy,
  error,
  onClose,
  onSubmit,
}) {
  const [documentType, setDocumentType] = useState(
    document ? "QUOTATION" : "QUOTATION"
  );
  const [bookingId, setBookingId] = useState(
    document ? String(document.bookingId) : ""
  );
  const selectedBooking = bookings.find(
    (booking) => String(booking.bookingId) === bookingId
  );
  const customer = selectedBooking
    ? customers.find(
        (item) => String(item.custId) === String(selectedBooking.custId)
      )
    : null;
  const bookingAmount = selectedBooking?.bookingTripfare;
  const initialAmount =
    document?.amount ??
    (bookingAmount !== null && bookingAmount !== undefined
      ? Number(bookingAmount)
      : "");

  return (
    <Modal
      title={title}
      subtitle="Document details are saved to the PostgreSQL documents table."
      onClose={onClose}
    >
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        {!document && (
          <Field label="Document type">
            <select
              name="documentType"
              value={documentType}
              onChange={(event) => setDocumentType(event.target.value)}
              className={inputClass}
            >
              <option value="QUOTATION">Quotation</option>
              <option value="INVOICE">Invoice</option>
            </select>
          </Field>
        )}
        {!document && (
          <p className="rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-800">
            Purchase orders require an approved quotation. Receipts require an
            issued invoice and confirmed payment.
          </p>
        )}
        <Field label="Booking">
          <select
            name="bookingId"
            value={bookingId}
            onChange={(event) => setBookingId(event.target.value)}
            required
            className={inputClass}
          >
            <option value="">Select a booking</option>
            {bookings.map((booking) => (
              <option key={booking.bookingId} value={booking.bookingId}>
                Booking #{booking.bookingId} · {formatDate(booking.bookingTripdate)}
                {booking.bookingTripfare != null
                  ? ` · ${formatCurrency(booking.bookingTripfare)}`
                  : ""}
              </option>
            ))}
          </select>
          {bookings.length === 0 && (
            <span className="mt-1 block text-xs text-rose-600">
              No bookings are available. Create a booking before making a document.
            </span>
          )}
        </Field>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
            <UserRound size={14} />
            Customer from selected booking
          </div>
          <p className="mt-2 text-sm font-semibold text-slate-800">
            {customer?.custName ||
              (selectedBooking ? `Customer #${selectedBooking.custId}` : "Select a booking")}
          </p>
          {customer && (
            <p className="mt-1 text-xs text-slate-500">
              {customer.custEmail || "No email"} · {customer.custPhoneno || "No phone"}
            </p>
          )}
        </div>
        <Field label="Amount (MYR)">
          <input
            key={`${bookingId}-${document?.documentId || "new"}`}
            name="amount"
            type="number"
            min="0.01"
            max="99999999.99"
            step="0.01"
            defaultValue={initialAmount}
            required
            className={inputClass}
          />
        </Field>
        <Field label="Remarks">
          <textarea
            name="remarks"
            rows={3}
            maxLength={5000}
            defaultValue={document?.remarks || ""}
            className={inputClass}
            placeholder="Optional notes or service description"
          />
        </Field>
        {error && <FormError message={error} />}
        <ModalActions
          onClose={onClose}
          busy={busy}
          submitLabel={
            busy
              ? "Saving..."
              : document
              ? "Save Changes"
              : `Create ${typeLabel(documentType)}`
          }
          disabled={bookings.length === 0}
        />
      </form>
    </Modal>
  );
}

const inputClass =
  "mt-1 block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function Field({ label, children }) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      {label}
      {children}
    </label>
  );
}

function ModalActions({ onClose, busy, submitLabel, disabled = false }) {
  return (
    <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
      <button
        type="button"
        onClick={onClose}
        disabled={busy}
        className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
      >
        Cancel
      </button>
      <button
        type="submit"
        disabled={busy || disabled}
        className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy && <LoaderCircle size={15} className="mr-2 inline animate-spin" />}
        {submitLabel}
      </button>
    </div>
  );
}

function FormError({ message }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}

function DocumentDetailsModal({ document, onClose }) {
  const fields = [
    ["Document number", document.documentNumber],
    ["Type", typeLabel(document.documentType)],
    ["Status", document.status],
    ["Booking", `#${document.bookingId}`],
    ["Customer", document.custName],
    ["Customer ID", document.custId],
    ["Email", document.custEmail || "—"],
    ["Phone", document.custPhone || "—"],
    ["Amount", formatCurrency(document.amount)],
    ["Created", formatDate(document.createdAt)],
  ];

  return (
    <Modal
      title={document.documentNumber}
      subtitle="Document details"
      onClose={onClose}
      maxWidth="max-w-2xl"
    >
      <div className="grid gap-3 p-5 sm:grid-cols-2">
        {fields.map(([label, value], index) => {
          const Icon = [CalendarDays, UserRound, CreditCard][index % 3];
          return (
            <div
              key={label}
              className="rounded-xl border border-slate-100 bg-slate-50 p-3"
            >
              <p className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <Icon size={13} />
                {label}
              </p>
              <p className="mt-1.5 break-words text-sm font-semibold text-slate-800">
                {value}
              </p>
            </div>
          );
        })}
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 sm:col-span-2">
          <p className="text-xs font-medium text-slate-500">Remarks</p>
          <p className="mt-1.5 whitespace-pre-wrap text-sm text-slate-800">
            {document.remarks || "—"}
          </p>
        </div>
      </div>
      <div className="flex justify-end border-t border-slate-100 p-5 pt-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
        >
          Close
        </button>
      </div>
    </Modal>
  );
}

function ConfirmModal({
  title,
  message,
  busy,
  error,
  onClose,
  onConfirm,
}) {
  return (
    <Modal title={title} onClose={onClose}>
      <div className="space-y-4 p-5">
        <p className="text-sm leading-6 text-slate-600">{message}</p>
        {error && <FormError message={error} />}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
          >
            {busy ? "Deleting..." : "Delete"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

function ReceiptModal({ document, busy, error, onClose, onSubmit }) {
  return (
    <Modal
      title={`Generate receipt for ${document.documentNumber}`}
      subtitle={`Receipt amount: ${formatCurrency(document.amount)}`}
      onClose={onClose}
    >
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        <Field label="Payment reference">
          <input
            name="paymentReference"
            required
            maxLength={100}
            className={inputClass}
            placeholder="Bank transfer or transaction reference"
          />
        </Field>
        <Field label="Payment method">
          <input
            name="paymentMethod"
            maxLength={100}
            className={inputClass}
            placeholder="Bank transfer, card, cash..."
          />
        </Field>
        <label className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <input
            type="checkbox"
            name="paymentConfirmed"
            required
            className="mt-0.5 accent-blue-600"
          />
          <span>
            I confirm this invoice has been paid and the payment reference has
            been verified.
          </span>
        </label>
        <p className="text-xs leading-5 text-slate-500">
          The current database schema does not link documents to payment
          records. The confirmation and reference will be stored in this
          receipt&apos;s remarks.
        </p>
        {error && <FormError message={error} />}
        <ModalActions
          onClose={onClose}
          busy={busy}
          submitLabel={busy ? "Generating..." : "Confirm Payment & Generate"}
        />
      </form>
    </Modal>
  );
}
