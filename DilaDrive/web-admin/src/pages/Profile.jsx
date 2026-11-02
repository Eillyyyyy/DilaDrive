import { useEffect, useState } from "react";
import {
  UserRound,
  Mail,
  Phone,
  ShieldCheck,
  Pencil,
  Save,
  X,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

export default function Profile({ user, onSave }) {
  const getProfile = () => ({
    name: user?.name || "DilaDrive Admin",
    email: user?.email || "",
    phone: user?.phone || "",
    role: user?.role || "Administrator",
  });

  const [form, setForm] = useState(getProfile);
  const [originalForm, setOriginalForm] = useState(getProfile);
  const [isEditing, setIsEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState("");

  useEffect(() => {
    const profile = getProfile();
    setForm(profile);
    setOriginalForm(profile);
  }, [user]);

  function handleChange(e) {
    const { name, value } = e.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));

    setMessage("");
  }

  function handleCancel() {
    setForm(originalForm);
    setIsEditing(false);
    setMessage("");
  }

  async function handleSubmit(e) {
    e.preventDefault();

    const updatedProfile = {
      ...form,
      name: form.name.trim(),
      email: form.email.trim().toLowerCase(),
      phone: form.phone.trim(),
    };

    if (!updatedProfile.name || !updatedProfile.email) {
      setMessage("Please enter your full name and email address.");
      setMessageType("error");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(updatedProfile.email)) {
      setMessage("Please enter a valid email address.");
      setMessageType("error");
      return;
    }

    if (
      updatedProfile.phone &&
      !/^[0-9+\-\s()]{8,20}$/.test(updatedProfile.phone)
    ) {
      setMessage("Please enter a valid phone number.");
      setMessageType("error");
      return;
    }

    if (typeof onSave !== "function") {
      setMessage("Unable to save profile. Please connect the save function.");
      setMessageType("error");
      return;
    }

    try {
      setSaving(true);
      setMessage("");

      await onSave(updatedProfile);

      setForm(updatedProfile);
      setOriginalForm(updatedProfile);
      setIsEditing(false);
      setMessage("Profile updated successfully.");
      setMessageType("success");
    } catch (error) {
      setMessage(error?.message || "Failed to update profile.");
      setMessageType("error");
    } finally {
      setSaving(false);
    }
  }

  const initials = (form.name || "Admin")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">
            My Profile
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            View and manage your administrator information.
          </p>
        </div>

        {!isEditing && (
          <button
            type="button"
            onClick={() => {
              setMessage("");
              setIsEditing(true);
            }}
            className="flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-800"
          >
            <Pencil size={17} />
            Edit Profile
          </button>
        )}
      </div>

      {/* Profile Summary */}
      <div className="rounded-2xl bg-white p-5 shadow-sm sm:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-teal-50 text-2xl font-bold text-teal-700">
            {initials || <UserRound size={32} />}
          </div>

          <div className="min-w-0">
            <h3 className="break-words text-xl font-bold text-gray-800">
              {form.name || "Administrator"}
            </h3>

            <p className="mt-1 break-all text-sm text-gray-500">
              {form.email || "No email address added"}
            </p>

            <span className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-3 py-1 text-xs font-semibold text-teal-700">
              <ShieldCheck size={14} />
              {form.role}
            </span>
          </div>
        </div>
      </div>

      {/* Success / Error Message */}
      {message && (
        <div
          role="status"
          aria-live="polite"
          className={`flex items-start gap-2 rounded-xl p-4 text-sm ${
            messageType === "success"
              ? "bg-green-50 text-green-700"
              : "bg-red-50 text-red-700"
          }`}
        >
          {messageType === "success" ? (
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" />
          ) : (
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
          )}
          <span>{message}</span>
        </div>
      )}

      {/* Personal Information */}
      <form onSubmit={handleSubmit}>
        <div className="rounded-2xl bg-white p-5 shadow-sm sm:p-6">
          <div className="mb-6 flex items-center gap-3 border-b border-gray-100 pb-4">
            <div className="rounded-xl bg-teal-50 p-2.5 text-teal-700">
              <UserRound size={21} />
            </div>

            <div>
              <h3 className="text-lg font-bold text-gray-800">
                Personal Information
              </h3>
              <p className="mt-1 text-sm text-gray-500">
                Your personal details and contact information.
              </p>
            </div>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            <ProfileField
              label="Full Name"
              name="name"
              value={form.name}
              onChange={handleChange}
              editing={isEditing}
              icon={UserRound}
              placeholder="Enter full name"
              required
            />

            <ProfileField
              label="Email Address"
              name="email"
              value={form.email}
              onChange={handleChange}
              editing={isEditing}
              icon={Mail}
              type="email"
              placeholder="admin@example.com"
              required
            />

            <ProfileField
              label="Phone Number"
              name="phone"
              value={form.phone}
              onChange={handleChange}
              editing={isEditing}
              icon={Phone}
              type="tel"
              placeholder="Enter phone number"
            />

            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">
                Account Role
              </label>

              <div className="flex min-h-[46px] items-center gap-3 rounded-xl border border-gray-200 bg-gray-50 px-3.5">
                <ShieldCheck
                  size={18}
                  className="shrink-0 text-teal-700"
                />
                <span className="text-sm text-gray-600">
                  {form.role}
                </span>
              </div>

              <p className="mt-1.5 text-xs text-gray-400">
                Role can only be changed by an authorized administrator.
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          {isEditing && (
            <div className="mt-7 flex flex-col-reverse gap-3 border-t border-gray-100 pt-5 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={handleCancel}
                disabled={saving}
                className="flex items-center justify-center gap-2 rounded-xl border border-gray-300 bg-white px-5 py-2.5 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-60"
              >
                <X size={17} />
                Cancel
              </button>

              <button
                type="submit"
                disabled={saving}
                className="flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Save size={17} />
                {saving ? "Saving..." : "Save Changes"}
              </button>
            </div>
          )}
        </div>
      </form>
    </div>
  );
}

function ProfileField({
  label,
  name,
  value,
  onChange,
  editing,
  icon: Icon,
  type = "text",
  placeholder,
  required = false,
}) {
  return (
    <div>
      <label
        htmlFor={`profile-${name}`}
        className="mb-2 block text-sm font-medium text-gray-700"
      >
        {label}
        {required && <span className="ml-1 text-red-500">*</span>}
      </label>

      <div
        className={`flex min-h-[46px] items-center gap-3 rounded-xl border px-3.5 transition ${
          editing
            ? "border-gray-300 bg-white focus-within:border-teal-600 focus-within:ring-2 focus-within:ring-teal-100"
            : "border-gray-200 bg-gray-50"
        }`}
      >
        <Icon size={18} className="shrink-0 text-gray-400" />

        <input
          id={`profile-${name}`}
          name={name}
          type={type}
          value={value}
          onChange={onChange}
          readOnly={!editing}
          required={required}
          maxLength={name === "email" ? 254 : name === "phone" ? 20 : 100}
          placeholder={placeholder}
          autoComplete={
            name === "name"
              ? "name"
              : name === "email"
                ? "email"
                : "tel"
          }
          className="w-full min-w-0 bg-transparent py-3 text-sm text-gray-800 outline-none placeholder:text-gray-400 read-only:cursor-default"
        />
      </div>
    </div>
  );
}