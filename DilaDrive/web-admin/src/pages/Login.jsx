import { useState } from "react";
import {
  Truck,
  Mail,
  LockKeyhole,
  Eye,
  EyeOff,
} from "lucide-react";

export default function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    const cleanEmail = email.trim();

    if (!cleanEmail || !password) {
      setError("Please enter your email and password.");
      return;
    }

    setError("");
    setLoading(true);

    try {
      await onLogin({
        email: cleanEmail,
        password,
        rememberMe,
      });
    } catch (err) {
      setError(
        err?.message || "Unable to sign in. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleForgotPassword = () => {
    alert(
      "Please contact your system administrator to reset your password."
    );
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-100 px-4 py-10">

      {/* Logo Outside the Card */}
      <div className="mb-7 flex flex-col items-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-[#27B5A8] text-white shadow-sm">
          <Truck size={32} strokeWidth={2} />
        </div>

        <h1 className="mt-3 text-3xl font-bold tracking-tight text-[#020B2D]">
          DilaDrive
        </h1>

        <p className="mt-1 text-sm text-slate-500">
          Transport Management System
        </p>
      </div>

      {/* Login Card */}
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-sm sm:p-8">

        {/* Card Heading */}
        <div className="mb-7">
          <h2 className="text-2xl font-bold text-slate-800">
            Admin Login
          </h2>

          <p className="mt-2 text-sm text-slate-500">
            Sign in to access your dashboard.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">

          {/* Email Address */}
          <div>
            <label
              htmlFor="email"
              className="mb-2 block text-sm font-medium text-slate-700"
            >
              Email Address
            </label>

            <div className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 transition focus-within:border-[#27B5A8] focus-within:ring-2 focus-within:ring-teal-50">
              <Mail
                size={18}
                className="shrink-0 text-slate-400"
              />

              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Enter your email"
                autoComplete="username"
                className="w-full bg-transparent py-3.5 text-sm text-slate-800 outline-none placeholder:text-slate-400"
                required
              />
            </div>
          </div>

          {/* Password */}
          <div>
            <label
              htmlFor="password"
              className="mb-2 block text-sm font-medium text-slate-700"
            >
              Password
            </label>

            <div className="flex items-center gap-3 rounded-xl border border-slate-200 px-4 transition focus-within:border-[#27B5A8] focus-within:ring-2 focus-within:ring-teal-50">
              <LockKeyhole
                size={18}
                className="shrink-0 text-slate-400"
              />

              <input
                id="password"
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
                autoComplete="current-password"
                className="w-full bg-transparent py-3.5 text-sm text-slate-800 outline-none placeholder:text-slate-400"
                required
              />

              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="shrink-0 text-slate-400 transition hover:text-slate-700"
                aria-label={
                  showPassword ? "Hide password" : "Show password"
                }
              >
                {showPassword ? (
                  <EyeOff size={18} />
                ) : (
                  <Eye size={18} />
                )}
              </button>
            </div>
          </div>

          {/* Remember Me and Forgot Password */}
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
            <label className="flex cursor-pointer items-center gap-2 text-slate-600">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                className="h-4 w-4 rounded accent-teal-700"
              />

              Remember Me
            </label>

            <button
              type="button"
              onClick={handleForgotPassword}
              className="font-medium text-teal-700 transition hover:text-teal-900 hover:underline"
            >
              Forgot Password?
            </button>
          </div>

          {/* Error Message */}
          {error && (
            <div
              role="alert"
              className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
            >
              {error}
            </div>
          )}

          {/* Sign In Button */}
          <button
            type="submit"
            disabled={loading}
            className="flex w-full items-center justify-center rounded-xl bg-[#27B5A8] py-3.5 text-sm font-semibold text-white transition hover:bg-teal-700 focus:outline-none focus:ring-4 focus:ring-teal-100 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? "Signing In..." : "Sign In"}
          </button>

        </form>
      </div>

      {/* Footer */}
      <p className="mt-6 text-center text-xs text-slate-400">
        © 2026 DilaDrive Transport Management System
      </p>

    </div>
  );
}