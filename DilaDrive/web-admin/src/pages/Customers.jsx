import { useMemo, useState } from "react";
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
  Verified: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  Pending: "bg-amber-50 text-amber-700 ring-amber-600/20",
  "Pending Verification": "bg-amber-50 text-amber-700 ring-amber-600/20",
};

function StatusBadge({ status }) {
  const verified = status === "Verified";
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-inset ${
        statusStyles[status] ||
        (verified
          ? statusStyles.Verified
          : statusStyles["Pending Verification"])
      }`}
    >
      <span
        className={`h-1.5 w-1.5 rounded-full ${
          verified ? "bg-emerald-500" : "bg-amber-500"
        }`}
      />
      {verified ? "Verified" : "Pending verification"}
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

export default function Customers({ customers = [], onVerifyCustomer }) {
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [customerList, setCustomerList] = useState(customers);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerToVerify, setCustomerToVerify] = useState(null);

  const verifiedCount = customerList.filter(
    (customer) => customer.status === "Verified"
  ).length;
  const pendingCount = customerList.length - verifiedCount;

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
        (statusFilter === "Verified" && customer.status === "Verified") ||
        (statusFilter === "Pending" && customer.status !== "Verified");

      return matchesSearch && matchesStatus;
    });
  }, [customerList, search, statusFilter]);

  const handleVerify = (customer) => {
    setCustomerList((current) =>
      current.map((item) =>
        item.email === customer.email
          ? { ...item, status: "Verified" }
          : item
      )
    );

    if (selectedCustomer?.email === customer.email) {
      setSelectedCustomer({ ...selectedCustomer, status: "Verified" });
    }

    // Optional callback lets the parent component persist the change.
    onVerifyCustomer?.(customer);
    setCustomerToVerify(null);
  };

  return (
    <main className="min-h-full space-y-6 bg-slate-50/60 p-4 sm:p-6 lg:p-8">
      {/* Page heading */}
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-teal-700">
            <ShieldCheck size={16} />
            Customer management
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Customer accounts
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500 sm:text-base">
            Review registered customers and verify their accounts before they
            access transportation services.
          </p>
        </div>
        <div className="inline-flex w-fit items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-600 shadow-sm">
          <Users size={17} className="text-teal-700" />
          {customerList.length} total accounts
        </div>
      </section>

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
                  <option value="Pending">Pending</option>
                  <option value="Verified">Verified</option>
                </select>
              </label>
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50/80">
              <tr className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                <th className="px-6 py-4">Customer</th>
                <th className="px-6 py-4">Contact details</th>
                <th className="px-6 py-4">Customer type</th>
                <th className="px-6 py-4">Account status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredCustomers.map((customer) => {
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
                        {customer.status !== "Verified" && (
                          <button
                            type="button"
                            onClick={() => setCustomerToVerify(customer)}
                            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white transition hover:bg-teal-800 focus:outline-none focus:ring-4 focus:ring-teal-700/20"
                          >
                            <CheckCircle2 size={15} />
                            Verify
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredCustomers.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-6 py-16 text-center">
                    <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
                      <Search size={21} />
                    </div>
                    <p className="mt-4 font-semibold text-slate-900">
                      No customers found
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Try another search term or change the status filter.
                    </p>
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
                <InfoItem
                  icon={Building2}
                  label="Company address"
                  value={selectedCustomer.companyAddress || selectedCustomer.company_address}
                />
                <InfoItem
                  icon={UserRound}
                  label="Contact person"
                  value={selectedCustomer.contactPerson || selectedCustomer.contact_person}
                />
              </div>

              <p className="text-xs leading-5 text-slate-500">
                Verify the account only after reviewing the submitted information.
                Corporate account fields may be empty for normal customers.
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
              {selectedCustomer.status !== "Verified" && (
                <button
                  type="button"
                  onClick={() => setCustomerToVerify(selectedCustomer)}
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
                >
                  <ShieldCheck size={17} />
                  Verify account
                </button>
              )}
            </div>
          </section>
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
              Verify customer account?
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              This will mark the account as verified in this page. Make sure the
              customer details have been reviewed first.
            </p>
            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="font-semibold text-slate-900">
                {customerToVerify.name || "Unnamed customer"}
              </p>
              <p className="mt-1 break-all text-sm text-slate-500">
                {customerToVerify.email || "No email provided"}
              </p>
            </div>
            <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setCustomerToVerify(null)}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleVerify(customerToVerify)}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800"
              >
                <Check size={16} />
                Confirm verification
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
