import { useEffect, useMemo, useState } from "react";
import api from "../services/api";
import {
  ChevronLeft,
  ChevronRight,
  ImagePlus,
  Eye,
  Plus,
  Search,
  ShieldCheck,
  UserCheck,
  Users,
  X,
} from "lucide-react";

const PAGE_SIZE = 8;

const emptyForm = {
  name: "",
  email: "",
  phone: "",
  address: "",
  password: "",
  confirmPassword: "",
  profileImage: "",
  role: "ADMIN",
};

function getErrorMessage(error) {
  const response = error.response?.data;
  return (typeof response === "string" ? response : response?.message)
    || error.message
    || "Unable to complete the request.";
}

function SummaryCard({ label, value, helper, icon: Icon, tone }) {
  const tones = {
    teal: "bg-teal-50 text-teal-700",
    green: "bg-emerald-50 text-emerald-700",
  };

  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${tones[tone]}`}>
          <Icon size={19} />
        </span>
      </div>
      <p className="mt-4 text-3xl font-bold tracking-tight text-slate-900">{value}</p>
      <p className="mt-1 text-xs text-slate-500">{helper}</p>
    </article>
  );
}

function AdminFormModal({
  admin,
  canManageRoles,
  saving,
  onClose,
  onSave,
}) {
  const isEditing = Boolean(admin);
  const [form, setForm] = useState(() => ({
    ...emptyForm,
    ...(admin || {}),
    password: "",
    confirmPassword: "",
  }));
  const [error, setError] = useState("");

  function setField(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  function handleImageChange(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      setError("Choose a PNG, JPG, or WebP image.");
      event.target.value = "";
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      setError("The profile image must be 2 MB or smaller.");
      event.target.value = "";
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        setForm((current) => ({ ...current, profileImage: reader.result }));
        setError("");
      } else {
        setError("Unable to read the selected image.");
      }
    };
    reader.onerror = () => setError("Unable to read the selected image.");
    reader.readAsDataURL(file);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    if (!isEditing && form.password !== form.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    const payload = {
      name: form.name.trim(),
      email: form.email.trim().toLowerCase(),
      phone: form.phone.trim(),
      address: form.address.trim(),
      profileImage: form.profileImage || null,
    };
    if (canManageRoles) payload.role = form.role;
    if (!isEditing) {
      payload.password = form.password;
      payload.confirmPassword = form.confirmPassword;
    }

    try {
      await onSave(payload);
    } catch (saveError) {
      setError(getErrorMessage(saveError));
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !saving) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-form-title"
        className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-start justify-between border-b border-slate-100 bg-white px-6 py-5">
          <div>
            <h2 id="admin-form-title" className="text-xl font-bold text-slate-900">
              {isEditing ? "Edit Admin" : "Add Admin"}
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {isEditing ? "Update administrator account details." : "Add a new administrator account."}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            aria-label="Close dialog"
            className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5 p-6">
          {error && (
            <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm font-medium text-slate-700">
              Full Name
              <input
                name="name"
                value={form.name}
                onChange={setField}
                maxLength={120}
                autoComplete="name"
                required
                className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </label>
            <label className="space-y-1.5 text-sm font-medium text-slate-700">
              Email Address
              <input
                name="email"
                type="email"
                value={form.email}
                onChange={setField}
                maxLength={254}
                autoComplete="email"
                required
                className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </label>
            <label className="space-y-1.5 text-sm font-medium text-slate-700">
              Phone Number
              <input
                name="phone"
                type="tel"
                value={form.phone}
                onChange={setField}
                maxLength={30}
                autoComplete="tel"
                required
                className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </label>
            <label className="space-y-1.5 text-sm font-medium text-slate-700">
              Address
              <input
                name="address"
                value={form.address}
                onChange={setField}
                maxLength={500}
                autoComplete="street-address"
                required
                className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </label>
            {!isEditing && (
              <>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Password
                  <input
                    name="password"
                    type="password"
                    value={form.password}
                    onChange={setField}
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    required
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Confirm Password
                  <input
                    name="confirmPassword"
                    type="password"
                    value={form.confirmPassword}
                    onChange={setField}
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    required
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  />
                </label>
              </>
            )}
            {canManageRoles && (
              <label className="space-y-1.5 text-sm font-medium text-slate-700">
                Role
                <select
                  name="role"
                  value={form.role || "ADMIN"}
                  onChange={setField}
                  className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-3 outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                >
                  <option value="ADMIN">ADMIN</option>
                  <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                </select>
              </label>
            )}
          </div>

          <div className="rounded-xl border border-dashed border-slate-300 p-4">
            <div className="flex items-center gap-4">
              {form.profileImage ? (
                <img
                  src={form.profileImage}
                  alt="Selected admin profile"
                  className="h-14 w-14 rounded-full object-cover"
                />
              ) : (
                <span className="flex h-14 w-14 items-center justify-center rounded-full bg-teal-50 text-teal-700">
                  <ImagePlus size={22} />
                </span>
              )}
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-800">Profile Image <span className="font-normal text-slate-400">(optional)</span></p>
                <p className="mt-1 text-xs text-slate-500">PNG, JPG, or WebP, up to 2 MB.</p>
              </div>
              <label className="cursor-pointer rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50">
                Choose file
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={handleImageChange}
                  className="sr-only"
                />
              </label>
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-60"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving ? "Saving..." : isEditing ? "Save Changes" : "Add Admin"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function AdminViewModal({ admin, onClose }) {
  const details = [
    ["Email address", admin.email],
    ["Phone number", admin.phone],
    ["Address", admin.address],
    ["Role", admin.role],
    ["Status", admin.status],
    ["Created", admin.createdAt ? new Date(admin.createdAt).toLocaleString() : "—"],
  ];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-view-title"
        className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <h2 id="admin-view-title" className="text-xl font-bold text-slate-900">Admin details</h2>
            <p className="mt-1 text-sm text-slate-500">Administrator account information.</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close details" className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700">
            <X size={20} />
          </button>
        </div>
        <div className="p-6">
          <div className="mb-5 flex items-center gap-4">
            {admin.profileImage ? (
              <img src={admin.profileImage} alt="" className="h-14 w-14 rounded-full object-cover" />
            ) : (
              <span className="flex h-14 w-14 items-center justify-center rounded-full bg-teal-50 text-lg font-bold text-teal-700">
                {(admin.name || "A").charAt(0).toUpperCase()}
              </span>
            )}
            <div>
              <h3 className="font-semibold text-slate-900">{admin.name || "Unnamed admin"}</h3>
              <p className="text-sm text-slate-500">Admin #{admin.id}</p>
            </div>
          </div>
          <dl className="divide-y divide-slate-100 rounded-xl border border-slate-200 px-4">
            {details.map(([label, value]) => (
              <div key={label} className="grid gap-1 py-3 sm:grid-cols-[130px_1fr] sm:gap-4">
                <dt className="text-sm text-slate-500">{label}</dt>
                <dd className="break-words text-sm font-medium text-slate-800">{value || "—"}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>
    </div>
  );
}

export default function Admins({ currentUser, onAdminUpdated }) {
  const [admins, setAdmins] = useState([]);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [modalAdmin, setModalAdmin] = useState(null);
  const [isCreating, setIsCreating] = useState(false);
  const [viewingAdmin, setViewingAdmin] = useState(null);
  const canManageRoles = currentUser?.role === "SUPER_ADMIN";

  async function loadAdmins() {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get("/admins");
      setAdmins(data);
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAdmins();
  }, []);

  const filteredAdmins = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return admins;
    return admins.filter((admin) =>
      [admin.name, admin.email, admin.phone, admin.role, admin.status]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [admins, search]);

  const totalPages = Math.max(1, Math.ceil(filteredAdmins.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageAdmins = filteredAdmins.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE,
  );
  const activeCount = admins.filter(
    (admin) => String(admin.status).toUpperCase() === "ACTIVE",
  ).length;

  useEffect(() => {
    setPage(1);
  }, [search]);

  async function saveAdmin(payload) {
    setSaving(true);
    try {
      const response = isCreating
        ? await api.post("/admins", payload)
        : await api.put(`/admins/${modalAdmin.id}`, payload);
      onAdminUpdated?.(response.data);
      setModalAdmin(null);
      setIsCreating(false);
      await loadAdmins();
    } finally {
      setSaving(false);
    }
  }

  function closeModal() {
    if (saving) return;
    setModalAdmin(null);
    setIsCreating(false);
  }

  return (
    <main className="admin-page">
      <section className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-teal-700">
            <ShieldCheck size={16} />
            User management
          </div>
          <h1 className="admin-page-title">Admins</h1>
          <p className="admin-page-description">
            View administrator accounts and manage access details.
          </p>
        </div>
        {canManageRoles && (
          <button
            type="button"
            onClick={() => {
              setIsCreating(true);
              setModalAdmin(null);
            }}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700"
          >
            <Plus size={18} />
            Add Admin
          </button>
        )}
      </section>

      <section className="grid gap-4 sm:grid-cols-2">
        <SummaryCard
          label="Total Admins"
          value={admins.length}
          helper="Administrator accounts"
          icon={Users}
          tone="teal"
        />
        <SummaryCard
          label="Active Admins"
          value={activeCount}
          helper="Accounts with active status"
          icon={UserCheck}
          tone="green"
        />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="font-semibold text-slate-900">Administrator accounts</h2>
            <p className="mt-1 text-xs text-slate-500">
              {filteredAdmins.length} {filteredAdmins.length === 1 ? "account" : "accounts"}
            </p>
          </div>
          <label className="flex w-full items-center gap-2 rounded-xl border border-slate-200 px-3.5 py-2.5 text-slate-400 focus-within:border-teal-500 sm:max-w-sm">
            <Search size={17} />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name, email, phone, or role"
              aria-label="Search administrators"
              className="w-full text-sm text-slate-700 outline-none placeholder:text-slate-400"
            />
          </label>
        </div>

        {error && (
          <div role="alert" className="m-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px] text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-5 py-4">No.</th>
                <th scope="col" className="px-5 py-4">Full Name</th>
                <th scope="col" className="px-5 py-4">Email</th>
                <th scope="col" className="px-5 py-4">Phone Number</th>
                <th scope="col" className="px-5 py-4">Role</th>
                <th scope="col" className="px-5 py-4">Status</th>
                <th scope="col" className="px-5 py-4">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">Loading administrators...</td>
                </tr>
              ) : pageAdmins.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    {search ? "No administrators match your search." : "No administrator accounts found."}
                  </td>
                </tr>
              ) : pageAdmins.map((admin, index) => (
                <tr key={admin.id} className="transition hover:bg-slate-50/70">
                  <td className="px-5 py-4 font-medium text-slate-500">
                    {(currentPage - 1) * PAGE_SIZE + index + 1}
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      {admin.profileImage ? (
                        <img src={admin.profileImage} alt="" className="h-9 w-9 rounded-full object-cover" />
                      ) : (
                        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-teal-50 font-semibold text-teal-700">
                          {(admin.name || "A").charAt(0).toUpperCase()}
                        </span>
                      )}
                      <span className="font-semibold text-slate-800">{admin.name}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-slate-600">{admin.email}</td>
                  <td className="px-5 py-4 text-slate-600">{admin.phone || "-"}</td>
                  <td className="px-5 py-4">
                    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                      admin.role === "SUPER_ADMIN"
                        ? "bg-violet-50 text-violet-700"
                        : "bg-slate-100 text-slate-700"
                    }`}>
                      {admin.role}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                      {admin.status}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <button
                      type="button"
                      onClick={() => setViewingAdmin(admin)}
                      aria-label={`View ${admin.name}`}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700"
                    >
                      <Eye size={14} />
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-3 border-t border-slate-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs text-slate-500">
            Page {currentPage} of {totalPages}
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              disabled={currentPage <= 1 || loading}
              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <ChevronLeft size={16} />
              Previous
            </button>
            <button
              type="button"
              onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
              disabled={currentPage >= totalPages || loading}
              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      {(modalAdmin || isCreating) && (
        <AdminFormModal
          admin={modalAdmin}
          canManageRoles={canManageRoles}
          saving={saving}
          onClose={closeModal}
          onSave={saveAdmin}
        />
      )}
      {viewingAdmin && (
        <AdminViewModal admin={viewingAdmin} onClose={() => setViewingAdmin(null)} />
      )}
    </main>
  );
}
