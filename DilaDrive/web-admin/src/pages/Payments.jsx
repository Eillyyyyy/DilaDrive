
import { useEffect, useMemo, useState } from "react";
import api from "../services/api";
import {
  Search,
  Eye,
  CheckCircle,
  X,
  ShieldCheck,
  FileText,
  Clock,
  CircleX,
  Wallet,
  ExternalLink,
  AlertCircle,
  RefreshCw,
} from "lucide-react";

const formatCurrency = (amount) =>
  new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
  }).format(Number(amount || 0));

const formatDateTime = (value) => {
  if (!value) return "-";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "-";

  return date.toLocaleString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const paymentProofUrl = (path) => {
  const base = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api")
    .replace(/\/+$/, "")
    .replace(/\/api$/, "");
  return new URL(path, `${base}/`).toString();
};

export default function Payments() {
  const [paymentList, setPaymentList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [reviewing, setReviewing] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");

  const [selectedPayment, setSelectedPayment] = useState(null);
  const [paymentToReview, setPaymentToReview] = useState(null);
  const [reviewAction, setReviewAction] = useState("");
  const [rejectionReason, setRejectionReason] = useState("");
  const [cashEntryOpen, setCashEntryOpen] = useState(false);
  const [cashBookingId, setCashBookingId] = useState("");
  const [cashAmount, setCashAmount] = useState("");
  const [cashRecording, setCashRecording] = useState(false);
  const [cashError, setCashError] = useState("");

  const [feedback, setFeedback] = useState(null);

  const loadPayments = async () => {
    setLoading(true);
    try {
      const response = await api.get("/payments");
      setPaymentList((response.data || []).map((payment) => ({
        id: payment.paymentId,
        bookingId: payment.bookingReference || `BK-${payment.bookingId}`,
        customer: payment.customerName || `Customer ${payment.customerId}`,
        amount: Number(payment.amount || 0),
        surchargeAmount: Number(payment.surchargeAmount || 0),
        paymentMethod: payment.paymentMethod,
        recordedBy: payment.recordedByDriverId
          ? `Driver #${payment.recordedByDriverId}`
          : payment.recordedByAdminId
            ? `Admin #${payment.recordedByAdminId}`
            : null,
        receipt: payment.proofUrl
          ? "Uploaded payment proof"
          : payment.paymentMethod === "CASH"
            ? "Cash recorded by staff"
            : "No proof uploaded",
        receiptUrl: payment.proofUrl,
        submittedAt: payment.submittedAt,
        reviewedAt: payment.verifiedAt,
        status: payment.paymentStatus === "VERIFIED"
          ? "Verified"
          : payment.paymentStatus === "REJECTED"
            ? "Rejected"
            : "Pending Verification",
        rejectionReason: payment.rejectReason,
      })));
      setFeedback(null);
    } catch (error) {
      setFeedback({
        type: "error",
        message: error.response?.data?.message || "Payment records could not be loaded. Check the API connection and retry.",
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPayments();
  }, []);

  const stats = useMemo(() => {
    const pending = paymentList.filter(
      (payment) => payment.status === "Pending Verification"
    );

    const verified = paymentList.filter(
      (payment) => payment.status === "Verified"
    );

    const rejected = paymentList.filter(
      (payment) => payment.status === "Rejected"
    );

    return {
      total: paymentList.length,
      pending: pending.length,
      verified: verified.length,
      rejected: rejected.length,
      verifiedAmount: verified.reduce(
        (total, payment) => total + Number(payment.amount || 0) + Number(payment.surchargeAmount || 0),
        0
      ),
    };
  }, [paymentList]);

  const filteredPayments = useMemo(() => {
    const query = search.trim().toLowerCase();

    return paymentList.filter((payment) => {
      const matchesSearch = [
        payment.id,
        payment.bookingId,
        payment.customer,
        payment.receipt,
      ].some((value) =>
        String(value || "").toLowerCase().includes(query)
      );

      const matchesStatus =
        statusFilter === "All" || payment.status === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [paymentList, search, statusFilter]);

  const statusStyle = (status) => {
    switch (status) {
      case "Verified":
        return "bg-emerald-100 text-emerald-700";
      case "Rejected":
        return "bg-red-100 text-red-700";
      default:
        return "bg-amber-100 text-amber-700";
    }
  };

  const updatePaymentStatus = async (paymentId, status, reason = "") => {
    const payment = paymentList.find(
      (item) => item.id === paymentId
    );

    if (!payment) return;

    if (payment.status !== "Pending Verification") {
      setFeedback({
        type: "error",
        message: "Only pending payments can be reviewed.",
      });
      closeReviewModal();
      return;
    }

    if (status === "Rejected" && !reason.trim()) {
      setFeedback({
        type: "error",
        message: "Please provide a reason for rejecting the payment.",
      });
      return;
    }

    setReviewing(true);
    try {
      await api.put(`/payments/${paymentId}/review`, {
        decision: status === "Verified" ? "VERIFIED" : "REJECTED",
        reason: status === "Rejected" ? reason.trim() : null,
      });
      await loadPayments();
      setFeedback({
        type: "success",
        message: status === "Verified"
          ? `Payment for ${payment.customer} has been verified and a receipt issued.`
          : `Payment for ${payment.customer} has been rejected. The reason was sent to the customer.`,
      });
      setSelectedPayment(null);
      closeReviewModal();
    } catch (error) {
      setFeedback({
        type: "error",
        message: error.response?.data?.message || "The payment decision could not be saved. Retry the review.",
      });
    } finally {
      setReviewing(false);
    }
  };

  const openReviewModal = (payment, action) => {
    if (payment.status !== "Pending Verification") {
      setFeedback({
        type: "error",
        message: "This payment has already been reviewed.",
      });
      return;
    }

    setPaymentToReview(payment);
    setReviewAction(action);
    setRejectionReason("");
    setFeedback(null);
  };

  const closeReviewModal = () => {
    setPaymentToReview(null);
    setReviewAction("");
    setRejectionReason("");
  };

  const recordCashPayment = async () => {
    const bookingId = Number(cashBookingId.trim());
    const amountText = cashAmount.trim();
    const amount = Number(amountText);
    if (!Number.isInteger(bookingId) || bookingId <= 0 || !/^\d+(?:\.\d{1,2})?$/.test(amountText) || !Number.isFinite(amount) || amount < 0.01) {
      setCashError("Enter a valid booking ID and cash amount with at most two decimal places.");
      return;
    }

    setCashError("");
    setCashRecording(true);
    try {
      await api.post(`/payments/bookings/${bookingId}/cash`, {
        amount: amount.toFixed(2),
      });
      setCashEntryOpen(false);
      setCashBookingId("");
      setCashAmount("");
      await loadPayments();
      setFeedback({
        type: "success",
        message: "Cash receipt recorded and sent for Admin review.",
      });
    } catch (error) {
      setCashError(error.response?.data?.message || "Cash received could not be recorded.");
    } finally {
      setCashRecording(false);
    }
  };

  return (
    <div className="admin-page">
      {/* Header */}
      <div className="admin-page-header">
        <div>
          <p className="mb-2 text-sm font-semibold uppercase tracking-wider text-teal-700">
            Finance & Transactions
          </p>

          <h1 className="admin-page-title">
            Payment Verification
          </h1>

          <p className="admin-page-description">
            Review customer payment receipts and record payment verification decisions.
          </p>
        </div>
        <button
          onClick={() => {
            setCashEntryOpen(true);
            setCashError("");
            setFeedback(null);
          }}
          className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 font-medium text-white hover:bg-slate-800"
        >
          <Wallet size={17} />
          Record cash received
        </button>
      </div>

      {/* Feedback */}
      {feedback && (
        <div
          className={`flex items-start gap-3 rounded-2xl border p-4 ${
            feedback.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-red-200 bg-red-50 text-red-800"
          }`}
        >
          {feedback.type === "success" ? (
            <CheckCircle size={18} className="mt-0.5 shrink-0" />
          ) : (
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
          )}

          <p className="text-sm">{feedback.message}</p>

          <button
            onClick={() => setFeedback(null)}
            className="ml-auto"
            aria-label="Dismiss notification"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Total Payments"
          value={stats.total}
          icon={Wallet}
          color="bg-blue-50 text-blue-700"
        />

        <StatCard
          title="Pending Review"
          value={stats.pending}
          icon={Clock}
          color="bg-amber-50 text-amber-700"
        />

        <StatCard
          title="Verified Payments"
          value={stats.verified}
          icon={CheckCircle}
          color="bg-emerald-50 text-emerald-700"
        />

        <StatCard
          title="Rejected Payments"
          value={stats.rejected}
          icon={CircleX}
          color="bg-red-50 text-red-700"
        />
      </div>

      {/* Verified Amount */}
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-slate-500">
          Total Amount of Verified Payments
        </p>

        <p className="mt-2 text-3xl font-bold text-slate-900">
          {formatCurrency(stats.verifiedAmount)}
        </p>

        <p className="mt-2 text-xs text-slate-500">
          This summary reflects verified records only; it is not a live bank balance.
        </p>
      </div>

      {/* Payment Records */}
      <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h3 className="text-lg font-semibold text-slate-900">
              Payment Records
            </h3>

            <p className="mt-1 text-sm text-slate-500">
              Review uploaded receipts and payment status.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              onClick={loadPayments}
              disabled={loading}
              className="inline-flex items-center justify-center gap-2 rounded-2xl border border-slate-200 px-4 py-3 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
              Refresh
            </button>
            <div className="relative w-full sm:w-64">
              <Search
                size={18}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                type="text"
                placeholder="Search payment..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 outline-none focus:border-teal-500 focus:bg-white"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-2xl border border-slate-200 bg-white px-4 py-3 outline-none focus:border-teal-500"
            >
              <option value="All">All statuses</option>
              <option value="Pending Verification">Pending Verification</option>
              <option value="Verified">Verified</option>
              <option value="Rejected">Rejected</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                {[
                  "No.",
                  "Booking / Customer",
                  "Amount",
                  "Receipt",
                  "Submitted At",
                  "Status",
                  "Actions",
                ].map((heading) => (
                  <th
                    key={heading}
                    className="whitespace-nowrap px-6 py-4 text-left font-medium"
                  >
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {loading && (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-slate-500">
                    Loading payment records…
                  </td>
                </tr>
              )}
              {filteredPayments.map((payment, index) => (
                <tr
                  key={payment.id}
                  className="hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-6 py-5 font-medium text-slate-500">
                    {index + 1}
                  </td>

                  <td className="px-6 py-5">
                    <p className="font-medium text-slate-900">
                      Booking {payment.bookingId}
                    </p>
                    <p className="mt-1 text-slate-500">
                      {payment.customer}
                    </p>
                  </td>

                  <td className="whitespace-nowrap px-6 py-5 font-medium">
                    {formatCurrency(payment.amount + payment.surchargeAmount)}
                    <p className="mt-1 text-xs font-normal text-slate-500">
                      {payment.paymentMethod?.replaceAll("_", " ")}
                    </p>
                  </td>

                  <td className="px-6 py-5">
                    {payment.receipt ? (
                      <span className="inline-flex items-center gap-2 text-slate-700">
                        <FileText size={16} className="text-teal-700" />
                        {payment.receipt}
                      </span>
                    ) : (
                      <span className="text-red-600">
                        No receipt uploaded
                      </span>
                    )}
                  </td>

                  <td className="whitespace-nowrap px-6 py-5 text-slate-600">
                    {formatDateTime(payment.submittedAt)}
                  </td>

                  <td className="whitespace-nowrap px-6 py-5">
                    <span
                      className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${statusStyle(
                        payment.status
                      )}`}
                    >
                      {payment.status}
                    </span>
                  </td>

                  <td className="px-6 py-5">
                    <div className="flex justify-center gap-2">
                      <button
                        onClick={() => setSelectedPayment(payment)}
                        title="View payment details"
                        className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 font-medium text-slate-700 hover:bg-slate-100"
                      >
                        <Eye size={16} />
                        View
                      </button>

                      {payment.status === "Pending Verification" && (
                        <button
                          onClick={() =>
                            openReviewModal(payment, "verify")
                          }
                          className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-3 py-2 font-medium text-white hover:bg-emerald-700"
                        >
                          <ShieldCheck size={16} />
                          Review
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}

              {!loading && filteredPayments.length === 0 && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-6 py-12 text-center text-slate-500"
                  >
                    No payment records match your search.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="border-t border-slate-100 px-6 py-4 text-sm text-slate-500">
          Showing {filteredPayments.length} of {paymentList.length} payments
        </div>
      </div>

      {/* Payment Details Modal */}
      {selectedPayment && (
        <Modal
          title="Payment Information"
          subtitle={`Payment for ${selectedPayment.customer}`}
          onClose={() => setSelectedPayment(null)}
        >
          <div className="space-y-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Info label="Booking Reference" value={selectedPayment.bookingId} />
              <Info label="Customer" value={selectedPayment.customer} />
              <Info
                label="Amount"
                value={formatCurrency(selectedPayment.amount + selectedPayment.surchargeAmount)}
              />
              <Info
                label="Card surcharge"
                value={formatCurrency(selectedPayment.surchargeAmount)}
              />
              <Info
                label="Payment Method"
                value={selectedPayment.paymentMethod}
              />
              <Info label="Recorded By" value={selectedPayment.recordedBy} />
              <Info
                label="Submitted At"
                value={formatDateTime(selectedPayment.submittedAt)}
              />
              <Info
                label="Reviewed At"
                value={formatDateTime(selectedPayment.reviewedAt)}
              />
            </div>

            <div className="rounded-2xl border border-slate-200 p-4">
              <p className="text-sm font-medium text-slate-500">
                Payment Status
              </p>

              <span
                className={`mt-2 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${statusStyle(
                  selectedPayment.status
                )}`}
              >
                {selectedPayment.status}
              </span>
            </div>

            <div className="rounded-2xl border border-slate-200 p-4">
              <p className="font-semibold text-slate-900">
                Uploaded Receipt
              </p>

              <p className="mt-1 break-all text-sm text-slate-500">
                {selectedPayment.receipt || "No receipt uploaded"}
              </p>

              {selectedPayment.receiptUrl ? (
                <a
                  href={paymentProofUrl(selectedPayment.receiptUrl)}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-teal-700 hover:bg-slate-50"
                >
                  Open Receipt
                  <ExternalLink size={15} />
                </a>
              ) : (
                <p className="mt-3 text-xs text-amber-700">
                  {selectedPayment.paymentMethod === "CASH"
                    ? "Cash was recorded by staff. Admin verification will issue the receipt."
                    : "Receipt preview is unavailable until an uploaded receipt URL is linked."}
                </p>
              )}
            </div>

            {selectedPayment.status === "Rejected" && (
              <div className="rounded-2xl border border-red-200 bg-red-50 p-4">
                <p className="font-semibold text-red-800">
                  Rejection Reason
                </p>
                <p className="mt-1 text-sm text-red-700">
                  {selectedPayment.rejectionReason || "No reason recorded."}
                </p>
              </div>
            )}

            {selectedPayment.status === "Pending Verification" && (
              <div className="flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
                <button
                  onClick={() =>
                    openReviewModal(selectedPayment, "reject")
                  }
                  className="inline-flex items-center justify-center gap-2 rounded-xl border border-red-200 px-4 py-2.5 font-medium text-red-700 hover:bg-red-50"
                >
                  <CircleX size={17} />
                  Reject Payment
                </button>

                <button
                  onClick={() =>
                    openReviewModal(selectedPayment, "verify")
                  }
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 font-medium text-white hover:bg-emerald-700"
                >
                  <ShieldCheck size={17} />
                  Verify Payment
                </button>
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* Verification / Rejection Confirmation */}
      {paymentToReview && (
        <Modal
          title={
            reviewAction === "verify"
              ? "Confirm Payment Verification"
              : "Reject Payment"
          }
          subtitle={`Payment for ${paymentToReview.customer} · Booking ${paymentToReview.bookingId}`}
          onClose={closeReviewModal}
          layer={60}
        >
          <div className="space-y-5">
            <div
              className={`rounded-2xl p-4 ${
                reviewAction === "verify"
                  ? "bg-emerald-50"
                  : "bg-red-50"
              }`}
            >
              <p className="font-semibold text-slate-900">
                {paymentToReview.customer}
              </p>

              <p className="mt-1 text-sm text-slate-600">
                {formatCurrency(paymentToReview.amount)}
              </p>

              <p className="mt-1 text-sm text-slate-600">
                Receipt: {paymentToReview.receipt || "Not uploaded"}
              </p>
            </div>

            {reviewAction === "verify" ? (
              <p className="text-sm leading-6 text-slate-600">
                Confirm only after checking that the receipt is readable and the payment details and amount match the relevant booking.
              </p>
            ) : (
              <div>
                <label
                  htmlFor="rejectionReason"
                  className="mb-2 block text-sm font-medium text-slate-700"
                >
                  Reason for rejection *
                </label>

                <textarea
                  id="rejectionReason"
                  rows={4}
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="Explain why the payment cannot be verified..."
                  className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-red-500"
                />

                <p className="mt-1 text-xs text-slate-500">
                  This reason should be recorded and made available to the customer.
                </p>
              </div>
            )}

            {feedback?.type === "error" && (
              <p className="text-sm text-red-600">{feedback.message}</p>
            )}

            <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
              <button
                onClick={closeReviewModal}
                className="rounded-xl border border-slate-200 px-5 py-2.5 font-medium text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>

              <button
                disabled={reviewing}
                onClick={() => {
                  if (reviewAction === "verify") {
                    updatePaymentStatus(
                      paymentToReview.id,
                      "Verified"
                    );
                  } else {
                    updatePaymentStatus(
                      paymentToReview.id,
                      "Rejected",
                      rejectionReason
                    );
                  }
                }}
                className={`rounded-xl px-5 py-2.5 font-medium text-white disabled:cursor-wait disabled:opacity-50 ${
                  reviewAction === "verify"
                    ? "bg-emerald-600 hover:bg-emerald-700"
                    : "bg-red-600 hover:bg-red-700"
                }`}
              >
                {reviewing
                  ? "Saving decision…"
                  : reviewAction === "verify"
                  ? "Confirm Verification"
                  : "Confirm Rejection"}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {cashEntryOpen && (
        <Modal
          title="Record cash received"
          subtitle="Record cash received for a completed booking. Admin verification is still required before a receipt is issued."
          onClose={() => !cashRecording && setCashEntryOpen(false)}
          layer={60}
        >
          <div className="space-y-4">
            <label className="block text-sm font-medium text-slate-700" htmlFor="cashBookingId">
              Booking ID
            </label>
            <input
              id="cashBookingId"
              type="number"
              min="1"
              step="1"
              value={cashBookingId}
              onChange={(event) => setCashBookingId(event.target.value)}
              className="w-full rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-500"
              placeholder="Database booking ID"
            />
            <label className="block text-sm font-medium text-slate-700" htmlFor="cashAmount">
              Cash received (RM)
            </label>
            <input
              id="cashAmount"
              type="number"
              min="0.01"
              step="0.01"
              value={cashAmount}
              onChange={(event) => setCashAmount(event.target.value)}
              className="w-full rounded-xl border border-slate-200 px-3 py-2.5 outline-none focus:border-teal-500"
              placeholder="0.00"
            />
            <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
              <button
                disabled={cashRecording}
                onClick={() => setCashEntryOpen(false)}
                className="rounded-xl border border-slate-200 px-4 py-2.5 font-medium text-slate-600 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                disabled={cashRecording}
                onClick={recordCashPayment}
                className="rounded-xl bg-slate-900 px-4 py-2.5 font-medium text-white disabled:opacity-50"
              >
              {cashRecording ? "Saving…" : "Record for review"}
              </button>
            {cashError && <p role="alert" className="text-sm text-red-700">{cashError}</p>}
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function StatCard({ title, value, icon: Icon, color }) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm text-slate-500">{title}</p>
          <p className="mt-2 text-3xl font-bold text-slate-900">{value}</p>
        </div>

        <div className={`rounded-2xl p-3 ${color}`}>
          <Icon size={22} />
        </div>
      </div>
    </div>
  );
}

function Modal({ title, subtitle, onClose, children, layer = 50 }) {
  return (
    <div
      className="fixed inset-0 flex items-center justify-center bg-slate-950/50 p-4"
      style={{ zIndex: layer }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-3xl bg-white shadow-xl">
        <div className="sticky top-0 flex items-start justify-between border-b border-slate-100 bg-white p-6">
          <div>
            <h3 className="text-xl font-bold text-slate-900">{title}</h3>
            {subtitle && (
              <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
            )}
          </div>

          <button
            onClick={onClose}
            aria-label="Close modal"
            className="rounded-xl p-2 text-slate-500 hover:bg-slate-100"
          >
            <X size={20} />
          </button>
        </div>

        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}

function Info({ label, value }) {
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 break-words font-medium text-slate-900">
        {value || "-"}
      </p>
    </div>
  );
}
