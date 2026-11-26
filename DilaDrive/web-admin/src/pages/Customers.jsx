import api from "../services/api";
import useRecordFocus from "../services/useRecordFocus";
import { useCallback, useMemo, useState, useEffect, useRef } from "react";
import {
  Search,
  Eye,
  CheckCircle2,
  X,
  ShieldCheck,
  Users,
  UserCheck,
  Clock3,
  Building2,
  Mail,
  Phone,
  UserRound,
  SlidersHorizontal,
  Check,
} from "lucide-react";

const statusStyles = {
  ACTIVE: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  PENDING: "bg-amber-50 text-amber-700 ring-amber-600/20",
  REJECTED: "bg-red-50 text-red-700 ring-red-600/20",
};
const statusLabels = { ACTIVE: "Active", PENDING: "Pending", REJECTED: "Rejected" };

function StatusBadge({ status }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-inset ${
        statusStyles[status] || "bg-slate-100 text-slate-700 ring-slate-300"
      }`}
    >
      {statusLabels[status] || status || "Unknown"}
    </span>
  );
}

function InfoItem({ icon: Icon, label, value }) {
  return (
    <div className="flex min-w-0 gap-3 rounded-xl border border-slate-100 bg-slate-50/70 p-4">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 shadow-sm ring-1 ring-slate-200/70">
        <Icon size={17} />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
          {label}
        </p>
        <p className="mt-1 break-words text-sm font-semibold text-slate-800">
          {value || "Not provided"}
        </p>
      </div>
    </div>
  );
}

export default function Customers({ onSessionExpired, recordFocus, onRecordOpened }) {
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [customerList, setCustomerList] = useState([]);
  const [customersLoading, setCustomersLoading] = useState(true);
  const [customersError, setCustomersError] = useState("");
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  useRecordFocus({ id: recordFocus, items: customerList, idKey: "id", loading: customersLoading,
    error: customersError, open: setSelectedCustomer, complete: onRecordOpened });
  const [customerToVerify, setCustomerToVerify] = useState(null);
  const [verifyingId, setVerifyingId] = useState(null);
  const [verificationError, setVerificationError] = useState("");
  const [verificationSuccess, setVerificationSuccess] = useState("");
  const [verificationDecision, setVerificationDecision] = useState("APPROVE");
  const [rejectionReason, setRejectionReason] = useState("");
  const [refreshRequired, setRefreshRequired] = useState(false);
  const verificationInFlight = useRef(false);
  const customerFetch = useRef(0);
  const [customerForm, setCustomerForm] = useState(null);
  const [savingCustomer, setSavingCustomer] = useState(false);
  const [saveError, setSaveError] = useState("");

  const openCustomerForm = (customer) => {
    setSelectedCustomer(null);
    setSaveError("");
    setCustomerForm({
      id: customer.id,
      custName: customer?.name ?? "",
      custEmail: customer?.email ?? "",
      custPhoneno: customer?.phone ?? "",
      custType: customer?.custType ?? "NORMAL",
      companyName: customer?.companyName ?? "",
      companyRegistrationNumber: customer?.companyRegistrationNumber ?? "",
    });
  };

  const changeCustomerField = (event) => {
    const { name, value } = event.target;
    setCustomerForm((current) => ({ ...current, [name]: value }));
  };

  const fetchCustomers = useCallback(async () => {
    const requestId = ++customerFetch.current;
    setCustomersLoading(true);
    setCustomersError("");
    try {
      const { data } = await api.get("/customers", { timeout: 15000 });
      if (requestId !== customerFetch.current) return null;
      const customers = data.map((customer) => ({
        ...customer,
        id: customer.custId,
        name: customer.custName,
        email: customer.custEmail,
        phone: customer.custPhoneno,
        companyName: customer.companyName ?? customer.custCompanyname,
        companyRegistrationNumber: customer.companyRegistrationNumber,
        status: customer.custStatus?.toUpperCase() || "",
        customerType: customer.custType?.toUpperCase() === "COMPANY" ? "Corporate" : "Normal",
      }));
      setCustomerList(customers);
      setSelectedCustomer((previous) => previous
        ? customers.find((customer) => customer.id === previous.id) ?? null
        : null);
      setRefreshRequired(false);
      return customers;
    } catch (error) {
      if (requestId !== customerFetch.current) return null;
      if (error.response?.status === 401) onSessionExpired?.();
      const response = error.response?.data;
      setCustomersError(
        (typeof response === "string" ? response : response?.message)
          || error.message
          || "Unable to load customers.",
      );
      return null;
    } finally {
      if (requestId === customerFetch.current) setCustomersLoading(false);
    }
  }, [onSessionExpired]);

  useEffect(() => {
    fetchCustomers();
    return () => { customerFetch.current++; };
  }, [fetchCustomers]);

  const saveCustomer = async (event) => {
    event.preventDefault();
    if (savingCustomer) return;
    setSavingCustomer(true);
    setSaveError("");
    const { id, custType, companyName, companyRegistrationNumber, ...fields } = customerForm;
    const payload = {
      ...fields,
      custName: fields.custName.trim(),
      custEmail: fields.custEmail.trim(),
      custPhoneno: fields.custPhoneno.trim() || null,
      ...(custType === "COMPANY" && {
        companyName: companyName.trim(),
        companyRegistrationNumber: companyRegistrationNumber.trim(),
      }),
    };
    try {
      if (!payload.custName) {
        setSaveError("Enter the customer name.");
        return;
      }
      await api.put(`/customers/${id}`, payload);
      setCustomerForm(null);
      await fetchCustomers();
    } catch (error) {
      const response = error.response?.data;
      setSaveError((typeof response === "string" ? response : response?.message)
        || "Unable to save this customer.");
    } finally {
      setSavingCustomer(false);
    }
  };

  const verifiedCount = customerList.filter(
    (customer) => customer.status === "ACTIVE"
  ).length;
  const pendingCount = customerList.filter((customer) => customer.status === "PENDING").length;

  const filteredCustomers = useMemo(() => {
    const term = search.trim().toLowerCase();

    return customerList.filter((customer) => {
      const matchesSearch = [
        customer.name,
        customer.email,
        customer.phone,
        customer.companyName,
        customer.company_name,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term));

      const matchesStatus =
        statusFilter === "All" ||
        customer.status === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [customerList, search, statusFilter]);

  const askToVerify = (customer, decision) => {
    if (customer.status !== "PENDING" || refreshRequired || verificationInFlight.current) return;
    setVerificationError("");
    setVerificationSuccess("");
    setVerificationDecision(decision);
    setRejectionReason("");
    setCustomerToVerify(customer);
  };

  const handleVerify = async (customer) => {
    if (!customer?.id || customer.status !== "PENDING" || refreshRequired || verificationInFlight.current) return;
    const reason = rejectionReason.trim();
    if (verificationDecision === "REJECT" && (!reason || reason.length > 500)) {
      setVerificationError("Enter a rejection reason between 1 and 500 characters.");
      return;
    }

    verificationInFlight.current = true;
    setVerifyingId(customer.id);
    setVerificationError("");
    try {
      const { data } = await api.patch(`/customers/${customer.id}/verification`, {
        decision: verificationDecision,
        ...(verificationDecision === "REJECT" ? { reason } : {}),
      }, { timeout: 15000 });
      setCustomerList((current) =>
        current.map((item) => item.id === customer.id ? { ...item, status: data.status } : item)
      );
      setSelectedCustomer((current) =>
        current?.id === customer.id ? { ...current, status: data.status } : current
      );
      setCustomerToVerify(null);
      setVerificationSuccess(data.status === "ACTIVE" ? "Customer account approved." : "Customer account rejected.");
      setRefreshRequired(true);
      await fetchCustomers();
    } catch (error) {
      const response = error.response?.data;
      const status = error.response?.status;
      if (status === 401) {
        onSessionExpired?.();
        return;
      }
      if (!status || status === 409 || status === 404 || status >= 500) {
        setRefreshRequired(true);
        const latest = await fetchCustomers();
        const updated = latest?.find((item) => item.id === customer.id);
        setCustomerToVerify(updated?.status === "PENDING" ? updated : null);
      }
      setVerificationError(
        (typeof response === "string" ? response : response?.message)
          || error.message
          || "Unable to verify this customer. Please try again.",
      );
    } finally {
      verificationInFlight.current = false;
      setVerifyingId(null);
    }
  };

  return (
    <main className="admin-page">
      {/* Page heading */}
      <section className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-teal-700">
            <ShieldCheck size={16} />
            Customer management
          </div>
          <h1 className="admin-page-title">
            Customer accounts
          </h1>
          <p className="admin-page-description">
            Review registered customers and verify their accounts before they
            access transportation services.
          </p>
        </div>
      </section>
      {verificationSuccess && <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-800">{verificationSuccess}</p>}
      {!customerToVerify && verificationError && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{verificationError}</p>}
      {refreshRequired && (
        <button type="button" className="rounded-xl border px-4 py-2" onClick={fetchCustomers} disabled={customersLoading}>
          Refresh customer status
        </button>
      )}

      {/* Summary cards */}
      <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-slate-500">Total customers</p>
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-sky-50 text-sky-700">
              <Users size={19} />
            </span>
          </div>
          <p className="mt-4 text-3xl font-bold tracking-tight text-slate-900">
            {customerList.length}
          </p>
          <p className="mt-1 text-xs text-slate-500">Registered accounts</p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-slate-500">Verified</p>
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
              <UserCheck size={19} />
            </span>
          </div>
          <p className="mt-4 text-3xl font-bold tracking-tight text-slate-900">
            {verifiedCount}
          </p>
          <p className="mt-1 text-xs text-slate-500">Accounts approved</p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium text-slate-500">Awaiting review</p>
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 text-amber-700">
              <Clock3 size={19} />
            </span>
          </div>
          <p className="mt-4 text-3xl font-bold tracking-tight text-slate-900">
            {pendingCount}
          </p>
          <p className="mt-1 text-xs text-slate-500">Need admin verification</p>
        </div>
      </section>

      {/* Customer table */}
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 p-5 sm:p-6">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                Registered customers
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Search account details, review customer type, and manage verification.
              </p>
            </div>

            <div className="flex w-full flex-col gap-3 sm:flex-row xl:w-auto">
              <label className="relative block min-w-0 flex-1 sm:min-w-64">
                <span className="sr-only">Search customers</span>
                <Search
                  size={17}
                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search name, email, phone..."
                  className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                />
              </label>

              <label className="flex items-center gap-2 rounded-xl border border-slate-200 px-3.5 focus-within:border-teal-600">
                <SlidersHorizontal size={16} className="text-slate-500" />
                <span className="sr-only">Filter by status</span>
                <select
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value)}
                  className="min-w-28 bg-transparent py-2.5 text-sm font-medium text-slate-700 outline-none"
                >
                  <option value="All">All statuses</option>
                  <option value="PENDING">Pending</option>
                  <option value="ACTIVE">Active</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </label>
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50/80">
              <tr className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                <th className="px-6 py-4">No.</th>
                <th className="px-6 py-4">Customer</th>
                <th className="px-6 py-4">Contact details</th>
                <th className="px-6 py-4">Customer type</th>
                <th className="px-6 py-4">Account status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredCustomers.map((customer, index) => {
                const corporate =
                  customer.customerType === "Corporate" ||
                  customer.customer_type === "Corporate" ||
                  customer.companyName ||
                  customer.company_name;

                return (
                  <tr
                    key={customer.id ?? customer.email}
                    className="transition hover:bg-slate-50/80"
                  >
                    <td className="px-6 py-4 text-sm font-medium text-slate-500">
                      {index + 1}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal-50 text-sm font-bold text-teal-800">
                          {(customer.name || "C")
                            .split(" ")
                            .filter(Boolean)
                            .slice(0, 2)
                            .map((part) => part[0])
                            .join("")
                            .toUpperCase()}
                        </div>
                        <div>
                          <p className="font-semibold text-slate-900">
                            {customer.name || "Unnamed customer"}
                          </p>
                          <p className="mt-0.5 text-xs text-slate-500">
                            {customer.companyName ||
                              customer.company_name ||
                              "Individual account"}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-slate-700">
                        {customer.email || "No email"}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {customer.phone || "No phone number"}
                      </p>
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center gap-1.5 text-sm text-slate-600">
                        {corporate ? <Building2 size={16} /> : <UserRound size={16} />}
                        {corporate ? "Corporate" : "Normal"}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={customer.status} />
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedCustomer(customer)}
                          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 focus:outline-none focus:ring-4 focus:ring-slate-200"
                        >
                          <Eye size={15} />
                          View
                        </button>
                        {customer.status === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => askToVerify(customer, "APPROVE")}
                            disabled={refreshRequired || verifyingId !== null}
                            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white transition hover:bg-teal-800 focus:outline-none focus:ring-4 focus:ring-teal-700/20"
                          >
                            <CheckCircle2 size={15} />
                            Approve
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredCustomers.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-16 text-center">
                    <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                      <Search size={21} />
                    </div>
                    <p className="mt-4 font-semibold text-slate-900">
                      {customersLoading
                        ? "Loading customers..."
                        : customersError
                          ? "Customers could not be loaded"
                          : "No customers found"}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      {customersError || "Try another search term or change the status filter."}
                    </p>
                    {customersError && (
                      <button
                        type="button"
                        onClick={fetchCustomers}
                        className="mt-4 text-sm font-semibold text-teal-700 hover:text-teal-800"
                      >
                        Retry
                      </button>
                    )}
                    {(search || statusFilter !== "All") && (
                      <button
                        type="button"
                        onClick={() => {
                          setSearch("");
                          setStatusFilter("All");
                        }}
                        className="mt-4 text-sm font-semibold text-teal-700 hover:text-teal-800"
                      >
                        Clear filters
                      </button>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-2 border-t border-slate-200 bg-slate-50/60 px-5 py-3 text-xs text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span>
            Showing {filteredCustomers.length} of {customerList.length} accounts
          </span>
          <span>Account verification is managed by the administrator.</span>
        </div>
      </section>

      {/* Account details modal */}
      {selectedCustomer && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4 backdrop-blur-sm"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setSelectedCustomer(null);
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="customer-modal-title"
            className="my-8 w-full max-w-2xl overflow-hidden rounded-2xl bg-white shadow-2xl"
          >
            <div className="flex items-start justify-between border-b border-slate-200 p-5 sm:p-6">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-teal-800">
                  <UserRound size={22} />
                </div>
                <div>
                  <h2 id="customer-modal-title" className="text-xl font-bold text-slate-900">
                    Customer account
                  </h2>
                  <p className="mt-1 text-sm text-slate-500">
                    Review the registered customer information.
                  </p>
                </div>
              </div>
              <button
                type="button"
                aria-label="Close account details"
                onClick={() => setSelectedCustomer(null)}
                className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800"
              >
                <X size={19} />
              </button>
            </div>

            <div className="space-y-5 p-5 sm:p-6">
              <div className="flex flex-col gap-3 rounded-xl border border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-lg font-bold text-slate-900">
                    {selectedCustomer.name || "Unnamed customer"}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    {selectedCustomer.companyName ||
                      selectedCustomer.company_name ||
                      selectedCustomer.customerType ||
                      selectedCustomer.customer_type ||
                      "Normal customer"}
                  </p>
                </div>
                <StatusBadge status={selectedCustomer.status} />
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <InfoItem icon={Mail} label="Email address" value={selectedCustomer.email} />
                <InfoItem icon={Phone} label="Phone number" value={selectedCustomer.phone} />
                <InfoItem icon={UserRound} label="Customer category" value={selectedCustomer.customerType} />
                {selectedCustomer.customerType === "Corporate" && (
                  <>
                    <InfoItem icon={Building2} label="Company name" value={selectedCustomer.companyName} />
                    <InfoItem icon={Building2} label="Company registration number" value={selectedCustomer.companyRegistrationNumber} />
                  </>
                )}
              </div>

              <p className="text-xs leading-5 text-slate-500">
                Verify the account only after reviewing the submitted information.
              </p>
            </div>

            <div className="flex flex-col-reverse gap-3 border-t border-slate-200 bg-slate-50/70 p-5 sm:flex-row sm:justify-end sm:p-6">
              <button
                type="button"
                onClick={() => setSelectedCustomer(null)}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                Close
              </button>
              {selectedCustomer.status === "PENDING" && (
                <>
                  <button type="button" disabled={refreshRequired || verifyingId !== null}
                    onClick={() => askToVerify(selectedCustomer, "APPROVE")}
                    className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">
                    <ShieldCheck size={17} /> Approve account
                  </button>
                </>
              )}
            </div>
          </section>
        </div>
      )}

      {customerForm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/50 p-4">
          <form role="dialog" aria-modal="true" aria-labelledby="customer-form-title"
            onSubmit={saveCustomer} className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-2xl">
            <h2 id="customer-form-title" className="text-xl font-bold text-slate-900">
              Edit customer
            </h2>
            <fieldset disabled={savingCustomer} className="space-y-3">
              <label className="block text-sm font-medium">
                Name
                <input required name="custName" value={customerForm.custName} onChange={changeCustomerField}
                  className="mt-1 w-full rounded-lg border px-3 py-2" />
              </label>
              <label className="block text-sm font-medium">
                Email
                <input required type="email" name="custEmail" value={customerForm.custEmail} onChange={changeCustomerField}
                  className="mt-1 w-full rounded-lg border px-3 py-2" />
              </label>
              <label className="block text-sm font-medium">
                Phone
                <input type="tel" name="custPhoneno" value={customerForm.custPhoneno} onChange={changeCustomerField}
                  className="mt-1 w-full rounded-lg border px-3 py-2" />
              </label>
              {customerForm.custType === "COMPANY" && (
                <>
                  <label className="block text-sm font-medium">
                    Company or organization name
                    <input required maxLength={150} name="companyName" value={customerForm.companyName}
                      onChange={changeCustomerField} className="mt-1 w-full rounded-lg border px-3 py-2" />
                  </label>
                  <label className="block text-sm font-medium">
                    Company registration number (optional)
                    <input maxLength={30} name="companyRegistrationNumber" value={customerForm.companyRegistrationNumber}
                      onChange={changeCustomerField} className="mt-1 w-full rounded-lg border px-3 py-2" />
                  </label>
                </>
              )}
            </fieldset>
            {saveError && <p role="alert" className="text-sm text-rose-700">{saveError}</p>}
            <div className="flex justify-end gap-3">
              <button type="button" disabled={savingCustomer} onClick={() => setCustomerForm(null)}
                className="rounded-xl border px-4 py-2 text-sm font-semibold">
                Close
              </button>
              <button type="submit" disabled={savingCustomer}
                className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
                {savingCustomer ? "Saving…" : "Save customer"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Verification confirmation modal */}
      {customerToVerify && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="verify-modal-title"
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
              <ShieldCheck size={24} />
            </div>
            <h2 id="verify-modal-title" className="mt-4 text-xl font-bold text-slate-900">
              {verificationDecision === "REJECT" ? "Reject customer account?" : "Approve customer account?"}
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              {verificationDecision === "REJECT"
                ? "The customer will receive this reason and will remain unable to book."
                : "The customer will be notified and become eligible to book."}
            </p>
            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="font-semibold text-slate-900">
                {customerToVerify.name || "Unnamed customer"}
              </p>
              <p className="mt-1 break-all text-sm text-slate-500">
                {customerToVerify.email || "No email provided"}
              </p>
            </div>
            {verificationDecision === "REJECT" && (
              <label className="mt-4 block text-sm font-medium">
                Rejection reason
                <textarea className="mt-1 w-full rounded-lg border px-3 py-2"
                  maxLength={500} rows={3} value={rejectionReason}
                  disabled={verifyingId !== null}
                  onChange={(event) => setRejectionReason(event.target.value)} />
              </label>
            )}
            {verificationError && (
              <p
                role="alert"
                className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700"
              >
                {verificationError}
              </p>
            )}
            <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setCustomerToVerify(null)}
                disabled={verifyingId !== null}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleVerify(customerToVerify)}
                disabled={verifyingId !== null || refreshRequired}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800"
              >
                <Check size={16} />
                {verifyingId === customerToVerify.id
                  ? "Saving..."
                  : verificationDecision === "REJECT" ? "Confirm rejection" : "Confirm approval"}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
