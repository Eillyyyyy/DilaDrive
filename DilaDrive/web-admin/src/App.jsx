import { useState, useEffect } from "react";
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
  ShieldCheck,
} from "lucide-react";

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
import Admins from "./pages/Admins";
import api from "./services/api";

const menuItems = [
  { name: "Dashboard", icon: LayoutDashboard },

  { name: "Customers", icon: Users },
  { name: "Drivers", icon: Truck },
  { name: "Admins", icon: ShieldCheck },

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
  const [unreadNotifications, setUnreadNotifications] = useState(0);

  const [currentUser, setCurrentUser] = useState(() => {
    const savedUser = localStorage.getItem("currentUser");
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const [profile, setProfile] = useState({
    name: "DilaDrive Admin",
    email: "",
    phone: "",
    role: "ADMIN",
  });

  useEffect(() => {
    if (!currentUser) return undefined;
    let active = true;
    api
      .get("/auth/session")
      .then(({ data }) => {
        if (!active) return;
        const user = { id: data.id, email: data.email, role: data.role };
        setCurrentUser(user);
        setProfile(data);
        localStorage.setItem("currentUser", JSON.stringify(user));
        localStorage.setItem("profile", JSON.stringify(data));
      })
      .catch(() => {
        if (!active) return;
        localStorage.removeItem("currentUser");
        localStorage.removeItem("profile");
        setCurrentUser(null);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!currentUser) {
      setUnreadNotifications(0);
      return undefined;
    }
    let active = true;
    const refreshUnreadCount = async () => {
      try {
        const { data } = await api.get("/notifications");
        if (active && Array.isArray(data)) {
          setUnreadNotifications(data.filter((notification) => !notification.read).length);
        }
      } catch (error) {
        console.error("Unable to refresh notification count:", error);
      }
    };
    refreshUnreadCount();
    const interval = window.setInterval(refreshUnreadCount, 30000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [currentUser]);

  async function handleLogin(credentials) {
    try {
      const response = await api.post(
        "/auth/login",
        {
          email: credentials.email,
          password: credentials.password,
        },
      );

      if (!response.data?.email) {
        throw new Error("Invalid Email or Password");
      }

      const user = {
        id: response.data.id,
        email: response.data.email,
        role: response.data.role || "ADMIN",
      };

      setCurrentUser(user);

      localStorage.setItem("currentUser", JSON.stringify(user));
      setProfile(response.data);
      localStorage.setItem(
  "profile",
  JSON.stringify(response.data)
);
      setActivePage("Dashboard");
    } catch (error) {
      throw new Error(
        error.response?.data || error.message || "Unable to login",
      );
    }
  }

  async function handleLogout() {
    try {
      await api.post("/auth/logout");
    } catch (error) {
      console.error("Unable to invalidate the admin session.", error);
    } finally {
      localStorage.removeItem("currentUser");
      localStorage.removeItem("profile");
      setCurrentUser(null);
      setActivePage("Dashboard");
      setMobileMenu(false);
    }
  }

  async function handleSaveProfile(updatedProfile) {
    try {
      const response = await api.put("/auth/profile", updatedProfile);
      setProfile(response.data);
      const updatedUser = {
        id: response.data.id,
        email: response.data.email,
        role: response.data.role,
      };
      setCurrentUser(updatedUser);
      localStorage.setItem("currentUser", JSON.stringify(updatedUser));
      localStorage.setItem("profile", JSON.stringify(response.data));
      return response.data;
    } catch (error) {
      const response = error.response?.data;
      throw new Error(
        (typeof response === "string" ? response : response?.message)
          || error.message
          || "Unable to update profile",
      );
    }
  }

  async function handleChangePassword(currentPassword, newPassword) {
    try {
      const response = await api.post("/auth/profile/password", {
        currentPassword,
        newPassword,
      });
      return response.data;
    } catch (error) {
      throw new Error(
        error.response?.data || error.message || "Unable to change password",
      );
    }
  }

  function renderPage() {
    switch (activePage) {
      case "Dashboard":
        return <Dashboard onNavigate={setActivePage} />;

      case "Customers":
        return <Customers />;

      case "Drivers":
        return <Drivers />;

      case "Vehicles":
        return <Vehicles />;

      case "Bookings":
        return <Bookings onNavigate={setActivePage} />;

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
            onChangePassword={handleChangePassword}
          />
        );

      case "Admins":
        return (
          <Admins
            currentUser={currentUser}
            onAdminUpdated={(admin) => {
              if (admin.id !== currentUser.id) return;
              setProfile(admin);
              const updatedUser = {
                id: admin.id,
                email: admin.email,
                role: admin.role,
              };
              setCurrentUser(updatedUser);
              localStorage.setItem("currentUser", JSON.stringify(updatedUser));
              localStorage.setItem("profile", JSON.stringify(admin));
            }}
          />
        );

      default:
        return <Dashboard onNavigate={setActivePage} />;
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
            <h1 className="text-lg font-bold">DilaDrive</h1>

            <p className="text-xs text-slate-400">TRANSPORT MANAGEMENT</p>
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
            {profile.profileImage ? (
              <img
                src={profile.profileImage}
                alt=""
                className="h-10 w-10 rounded-full object-cover"
              />
            ) : (
              <div className="grid h-10 w-10 place-items-center rounded-full bg-teal-100 font-bold text-teal-700">
                {(profile.name || "A").charAt(0).toUpperCase()}
              </div>
            )}

            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{profile.name}</p>

              <p className="truncate text-xs text-slate-400">{profile.role}</p>
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
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setMobileMenu(true)}
              className="rounded-lg p-2 hover:bg-slate-100 md:hidden"
            >
              <Menu size={22} />
            </button>

            <p className="text-sm font-medium text-slate-500">{activePage}</p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setActivePage("Notifications")}
              aria-label={unreadNotifications ? `Notifications, ${unreadNotifications} unread` : "Notifications"}
              className="relative rounded-xl border p-2.5 hover:bg-slate-50"
            >
              <Bell size={19} />
              {unreadNotifications > 0 && (
                <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">
                  {unreadNotifications > 99 ? "99+" : unreadNotifications}
                </span>
              )}
            </button>

            <button
              onClick={() => setActivePage("Profile")}
              className="flex items-center gap-2 rounded-xl p-1 hover:bg-slate-50"
            >
              {profile.profileImage ? (
                <img
                  src={profile.profileImage}
                  alt=""
                  className="h-10 w-10 rounded-full object-cover"
                />
              ) : (
                <div className="grid h-10 w-10 place-items-center rounded-full bg-teal-100 font-bold text-teal-700">
                  {(profile.name || "A").charAt(0).toUpperCase()}
                </div>
              )}

              <div className="hidden sm:block text-left">
                <p className="text-sm font-semibold">{profile.name}</p>

                <p className="text-xs text-slate-500">{profile.role}</p>
              </div>
            </button>
          </div>
        </header>

        {/* Content */}
        <section className="p-4 sm:p-6 lg:p-8">
          <div className="mx-auto w-full max-w-[1600px]">
          {renderPage()}
          </div>
        </section>
      </main>
    </div>
  );
}
