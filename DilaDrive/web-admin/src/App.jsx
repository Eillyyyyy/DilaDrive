import { useState } from "react";
import {
  LayoutDashboard,
  Users,
  Truck,
  CarFront,
  CalendarDays,
  MapPinned,
  CreditCard,
  FileText,
  Bell,
  Menu,
  X,
  LogOut,
  UserRound,
} from "lucide-react";

import {
  bookingsSeed,
  customers,
  drivers,
  vehicles,
} from "./data/demoData";

import Dashboard from "./pages/Dashboard";
import Customers from "./pages/Customers";
import Drivers from "./pages/Drivers";
import Vehicles from "./pages/Vehicles";
import Bookings from "./pages/Bookings";
import Assignments from "./pages/Assignments";
import Payments from "./pages/Payments";
import Documents from "./pages/Documents";
import Notifications from "./pages/Notifications";
import Login from "./pages/Login";
import Profile from "./pages/Profile";

const menuItems = [
  { name: "Dashboard", icon: LayoutDashboard },

  { name: "Customers", icon: Users },
  { name: "Drivers", icon: Truck },

  { name: "Vehicles", icon: CarFront },

  { name: "Bookings", icon: CalendarDays },
  { name: "Assignments", icon: MapPinned },

  { name: "Payments", icon: CreditCard },

  { name: "Documents", icon: FileText },

  { name: "Notifications", icon: Bell },

  { name: "Profile", icon: UserRound },
];

export default function App() {
  const [activePage, setActivePage] = useState("Dashboard");
  const [mobileMenu, setMobileMenu] = useState(false);
  const [bookings, setBookings] = useState(bookingsSeed);

  const [currentUser, setCurrentUser] = useState(null);

  const [profile, setProfile] = useState({
    name: "DilaDrive Admin",
    email: "",
    phone: "",
    role: "Administrator",
  });

  function handleLogin(credentials) {
    setCurrentUser(credentials);

    setProfile((prev) => ({
      ...prev,
      email: credentials.email,
    }));

    setActivePage("Dashboard");
  }

  function handleLogout() {
    setCurrentUser(null);
    setActivePage("Dashboard");
    setMobileMenu(false);
  }

  function handleSaveProfile(updatedProfile) {
    setProfile(updatedProfile);
  }

  function approveBooking(id) {
    setBookings((prev) =>
      prev.map((booking) =>
        booking.id === id
          ? { ...booking, status: "Approved" }
          : booking
      )
    );
  }

  function renderPage() {
    switch (activePage) {
      case "Dashboard":
        return (
          <Dashboard
            bookings={bookings}
            onNavigate={setActivePage}
          />
        );

      case "Customers":
        return (
          <Customers customers={customers} />
        );

      case "Drivers":
        return (
          <Drivers drivers={drivers} />
        );

      case "Vehicles":
        return (
          <Vehicles vehicles={vehicles} />
        );

      case "Bookings":
        return (
          <Bookings
            bookings={bookings}
            onApprove={approveBooking}
          />
        );

      case "Assignments":
        return <Assignments />;

      case "Payments":
        return <Payments />;

      case "Documents":
        return <Documents />;

      case "Notifications":
        return <Notifications />;

      case "Profile":
        return (
          <Profile
            user={profile}
            onSave={handleSaveProfile}
          />
        );

      default:
        return (
          <Dashboard
            bookings={bookings}
            onNavigate={setActivePage}
          />
        );
    }
  }

  if (!currentUser) {
    return <Login onLogin={handleLogin} />;
  }

  return (
    <div className="min-h-screen bg-slate-50">

      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-40 w-64
        flex-col bg-slate-950 text-white
        ${mobileMenu ? "flex" : "hidden"} md:flex`}
      >
        {/* Logo */}
        <div className="flex h-20 items-center gap-3 border-b border-white/10 px-6">

          <div className="grid h-10 w-10 place-items-center rounded-xl bg-teal-500 font-bold text-slate-950">
            D
          </div>

          <div>
            <h1 className="text-lg font-bold">
              DilaDrive
            </h1>

            <p className="text-xs text-slate-400">
              TRANSPORT MANAGEMENT
            </p>
          </div>

          <button
            onClick={() => setMobileMenu(false)}
            className="ml-auto md:hidden"
          >
            <X size={20} />
          </button>
        </div>

        <p className="px-5 pb-3 pt-6 text-xs uppercase tracking-widest text-slate-500">
          Workspace
        </p>

        {/* Navigation */}
        <nav className="flex-1 space-y-1 overflow-y-auto px-3">

          {menuItems.map(({ name, icon: Icon }) => (
            <button
              key={name}
              onClick={() => {
                setActivePage(name);
                setMobileMenu(false);
              }}
              className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm transition
              ${
                activePage === name
                  ? "bg-teal-500 font-semibold text-slate-950"
                  : "text-slate-300 hover:bg-white/10"
              }`}
            >
              <Icon size={18} />
              {name}
            </button>
          ))}
        </nav>

        {/* User */}
        <div className="border-t border-white/10 p-4">

          <div className="mb-3 flex items-center gap-3 px-2">

            <div className="grid h-10 w-10 place-items-center rounded-full bg-teal-100 font-bold text-teal-700">
              {(profile.name || "A")
                .charAt(0)
                .toUpperCase()}
            </div>

            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">
                {profile.name}
              </p>

              <p className="truncate text-xs text-slate-400">
                {profile.role}
              </p>
            </div>
          </div>

          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-sm text-red-300 hover:bg-red-500/10"
          >
            <LogOut size={18} />
            Logout
          </button>
        </div>
      </aside>

      {/* Mobile Overlay */}
      {mobileMenu && (
        <button
          onClick={() => setMobileMenu(false)}
          className="fixed inset-0 z-30 bg-black/40 md:hidden"
        />
      )}

      {/* Main */}
      <main className="min-h-screen md:ml-64">

        {/* Header */}
        <header className="sticky top-0 z-20 flex h-20 items-center justify-between border-b bg-white px-4 md:px-8">

          <div className="flex items-center gap-3">

            <button
              onClick={() => setMobileMenu(true)}
              className="rounded-lg p-2 hover:bg-slate-100 md:hidden"
            >
              <Menu size={22} />
            </button>

            <div>
              <p className="text-xs text-slate-500">
                Workspace / {activePage}
              </p>

              <h2 className="font-bold">
                {activePage}
              </h2>
            </div>
          </div>

          <div className="flex items-center gap-3">

            <button
              onClick={() =>
                setActivePage("Notifications")
              }
              className="rounded-xl border p-2.5 hover:bg-slate-50"
            >
              <Bell size={19} />
            </button>

            <button
              onClick={() => setActivePage("Profile")}
              className="flex items-center gap-2 rounded-xl p-1 hover:bg-slate-50"
            >
              <div className="grid h-10 w-10 place-items-center rounded-full bg-teal-100 font-bold text-teal-700">
                {(profile.name || "A")
                  .charAt(0)
                  .toUpperCase()}
              </div>

              <div className="hidden sm:block text-left">
                <p className="text-sm font-semibold">
                  {profile.name}
                </p>

                <p className="text-xs text-slate-500">
                  {profile.role}
                </p>
              </div>
            </button>

          </div>
        </header>

        {/* Content */}
        <section className="p-4 md:p-8">

          {activePage === "Dashboard" && (
            <div className="mb-6">
              <p className="text-sm text-slate-500">
                Welcome back, {profile.name}
              </p>
            </div>
          )}

          {renderPage()}
        </section>

      </main>
    </div>
  );
}