import { useState, useEffect, useRef, useCallback } from "react";
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
  Settings as SettingsIcon,
} from "lucide-react";

import Dashboard from "./pages/Dashboard";
import Customers from "./pages/Customers";
import Drivers from "./pages/Drivers";
import Vehicles from "./pages/Vehicles";
import Bookings from "./pages/Bookings";
import Assignments from "./pages/Assignments";
import Calendar from "./pages/Calendar";
import Payments from "./pages/Payments";
import Documents from "./pages/Documents";
import Notifications from "./pages/Notifications";
import NotificationBell from "./components/NotificationBell";
import useAdminNotifications, { notificationError } from "./services/useAdminNotifications";
import { notificationTarget } from "./services/notificationTargets";
import Login from "./pages/Login";
import Profile from "./pages/Profile";
import Admins from "./pages/Admins";
import Settings from "./pages/Settings";
import api, { adminSessionExpiredEvent } from "./services/api";

const menuItems = [
  { name: "Dashboard", icon: LayoutDashboard },

  { name: "Customers", icon: Users },
  { name: "Drivers", icon: Truck },
  { name: "Admins", icon: ShieldCheck },

  { name: "Vehicles", icon: CarFront },

  { name: "Bookings", icon: CalendarDays },
  { name: "Calendar", icon: CalendarDays },
  { name: "Assignments", icon: MapPinned },

  { name: "Payments", icon: CreditCard },

  { name: "Documents", icon: FileText },

  { name: "Notifications", icon: Bell },

  { name: "Profile", icon: UserRound },
  { name: "Settings", icon: SettingsIcon },
];

export default function App() {
  const [activePage, setActivePage] = useState("Dashboard");
  const [calendarDate, setCalendarDate] = useState(null);
  const openCalendar = date => {
    setCalendarDate(date);
    setActivePage("Calendar");
  };
  const openBooking = id => {
    setRecordFocus({ page: "Bookings", id });
    setActivePage("Bookings");
  };
  const [mobileMenu, setMobileMenu] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  const [notificationMessage, setNotificationMessage] = useState("");
  const [recordFocus, setRecordFocus] = useState(null);

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
  const [profileRequest, setProfileRequest] = useState({ state: "loading" });
  const [profileRetry, setProfileRetry] = useState(0);
  const [sessionExpired, setSessionExpired] = useState(false);
  const restoreSession = useRef(Boolean(currentUser));
  const profileOpen = activePage === "Profile";
  const userId = currentUser?.id;
  const inbox = useAdminNotifications(sessionExpired ? null : userId, activePage === "Notifications");
  useEffect(() => {
    if (!userId) return undefined;
    const expire = () => setSessionExpired(true);
    window.addEventListener(adminSessionExpiredEvent, expire);
    return () => window.removeEventListener(adminSessionExpiredEvent, expire);
  }, [userId]);
  const completeRecordFocus = useCallback(message => {
    setRecordFocus(null);
    if (message) setNotificationMessage(message);
  }, []);

  const clearAdminSession = useCallback(() => {
    localStorage.removeItem("currentUser");
    localStorage.removeItem("profile");
    setCurrentUser(null);
    setSessionExpired(false);
    setActivePage("Dashboard");
    setBellOpen(false);
    setRecordFocus(null);
    setNotificationMessage("");
  }, []);

  useEffect(() => {
    if (!profileOpen) setProfileRequest({ state: "loading" });
    if (!userId || (!profileOpen && !restoreSession.current)) return undefined;
    const request = new AbortController();
    if (profileOpen) setProfileRequest({ state: "loading" });
    api
      .get("/auth/session", { signal: request.signal })
      .then(({ data }) => {
        if (request.signal.aborted) return;
        restoreSession.current = false;
        const user = { id: data.id, email: data.email, role: data.role };
        setCurrentUser(user);
        setProfile(data);
        localStorage.setItem("currentUser", JSON.stringify(user));
        localStorage.setItem("profile", JSON.stringify(data));
        if (profileOpen) setProfileRequest({ state: "ready" });
      })
      .catch((error) => {
        if (request.signal.aborted) return;
        restoreSession.current = false;
        const status = error.response?.status;
        if (status === 401) {
          clearAdminSession();
          return;
        }
        if (!profileOpen) return;
        let message = "Unable to load your account details.";
        if (!error.response) message = "Cannot connect to DilaDrive. Check your connection.";
        else if (status === 404) message = "Admin account not found.";
        else if (status >= 500) message = "The server could not load your account right now.";
        setProfileRequest({ state: "error", message });
      });
    return () => request.abort();
  }, [profileOpen, profileRetry, userId]);

  async function markNotification(item) {
    setNotificationMessage("");
    try {
      return await inbox.markRead(item);
    } catch (error) {
      setNotificationMessage(notificationError(error));
      return false;
    }
  }

  async function openNotification(item) {
    if (!await markNotification(item)) return;
    setBellOpen(false);
    const target = notificationTarget(item);
    if (!target) {
      setNotificationMessage("This notification has no linked record. You can read the message in Notifications.");
      return;
    }
    if (target.page === "Bookings") openBooking(target.id);
    else {
      setRecordFocus(target);
      setActivePage(target.page);
    }
  }

  async function markNotificationsRead() {
    setNotificationMessage("");
    try {
      await inbox.markAllRead();
    } catch (error) {
      setNotificationMessage(notificationError(error));
    }
  }

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
      setSessionExpired(false);

      localStorage.setItem("currentUser", JSON.stringify(user));
      setProfile(response.data);
      localStorage.setItem(
  "profile",
  JSON.stringify(response.data)
);
      if (user.id !== userId) {
        setActivePage("Dashboard");
        setBellOpen(false);
        setRecordFocus(null);
        setNotificationMessage("");
      }
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
      clearAdminSession();
      setMobileMenu(false);
    }
  }

  async function handleSaveProfile(updatedProfile) {
    try {
      const response = await api.put("/auth/profile", updatedProfile, { timeout: 15000 });
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
      if (error.response?.status === 401) clearAdminSession();
      const response = error.response?.data;
      throw new Error(
        (typeof response === "string" ? response : response?.message)
          || error.message
          || "Unable to update profile",
      );
    }
  }

  async function loadEditProfile(signal) {
    try {
      const { data } = await api.get("/auth/session", { signal, timeout: 15000 });
      return data;
    } catch (error) {
      if (!signal.aborted && error.response?.status === 401) clearAdminSession();
      throw error;
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
    const focus = recordFocus?.page === activePage ? recordFocus.id : null;
    switch (activePage) {
      case "Dashboard":
        return <Dashboard onNavigate={setActivePage} onOpenCalendar={openCalendar} onOpenBooking={openBooking} />;

      case "Customers":
        return <Customers onSessionExpired={clearAdminSession} recordFocus={focus} onRecordOpened={completeRecordFocus} />;

      case "Drivers":
        return <Drivers recordFocus={focus} onRecordOpened={completeRecordFocus} />;

      case "Settings":
        return <Settings />;
      case "Vehicles":
        return <Vehicles recordFocus={focus} onRecordOpened={completeRecordFocus} />;

      case "Bookings":
        return <Bookings onNavigate={setActivePage} bookingFocus={focus} onBookingOpened={completeRecordFocus} />;

      case "Calendar":
        return <Calendar initialDate={calendarDate} onOpenBooking={openBooking} />;
      case "Assignments":
        return <Assignments onNavigate={setActivePage} recordFocus={focus} onRecordOpened={completeRecordFocus} />;

      case "Payments":
        return <Payments />;

      case "Documents":
        return <Documents />;

      case "Notifications":
        return <Notifications inbox={inbox} onOpen={openNotification} onRead={markNotification} onMarkAll={markNotificationsRead} />;

      case "Profile":
        if (profileRequest.state !== "ready") {
          return (
            <section className="admin-page">
              <h1 className="admin-page-title">Profile</h1>
              {profileRequest.state === "loading" ? (
                <p role="status" className="text-slate-600">Loading account details…</p>
              ) : (
                <div role="alert" className="space-y-3 text-slate-700">
                  <p>{profileRequest.message}</p>
                  <button type="button" className="rounded-lg bg-teal-700 px-4 py-2 text-white"
                    onClick={() => setProfileRetry((attempt) => attempt + 1)}>
                    Retry
                  </button>
                </div>
              )}
            </section>
          );
        }
        return (
          <Profile
            user={profile}
            onLoadEdit={loadEditProfile}
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
        return <Dashboard onNavigate={setActivePage} onOpenCalendar={openCalendar} onOpenBooking={openBooking} />;
    }
  }

  if (!currentUser) {
    return <Login onLogin={handleLogin} />;
  }

  return (
    <>
    <div key={userId} inert={sessionExpired ? "" : undefined} className="min-h-screen bg-slate-50">
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
            <NotificationBell inbox={inbox} open={bellOpen} setOpen={setBellOpen}
              onOpen={openNotification} onMarkAll={markNotificationsRead}
              onViewAll={() => { setBellOpen(false); setActivePage("Notifications"); }} />

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
          {notificationMessage && <div role="alert" className="mb-4 flex items-start justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
            <span>{notificationMessage}</span>
            <button type="button" className="font-semibold underline" onClick={() => setNotificationMessage("")}>Dismiss</button>
          </div>}
          {renderPage()}
          </div>
        </section>
      </main>
    </div>
    {sessionExpired && (
      <div className="fixed inset-0 z-[100] overflow-y-auto bg-slate-50"
           role="dialog" aria-modal="true" aria-label="Sign in again">
        <Login onLogin={handleLogin} sessionExpired />
      </div>
    )}
    </>
  );
}
