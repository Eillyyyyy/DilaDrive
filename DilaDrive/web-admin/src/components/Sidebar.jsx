import {
  LayoutDashboard,
  Users,
  Truck,
  CarFront,
  CalendarDays,
  CreditCard,
  FileText,
  Bell,
  MapPinned,
  UserRound,
  LogOut,
} from "lucide-react";

import { NavLink, useNavigate } from "react-router-dom";

const nav = [
  {
    section: "WORKSPACE",
    items: [
      {
        label: "Dashboard",
        path: "/",
        icon: LayoutDashboard,
      },
    ],
  },

  {
    section: "USER MANAGEMENT",
    items: [
      {
        label: "Customers",
        path: "/customers",
        icon: Users,
      },
      {
        label: "Drivers",
        path: "/drivers",
        icon: Truck,
      },
    ],
  },

  {
    section: "FLEET MANAGEMENT",
    items: [
      {
        label: "Vehicles",
        path: "/vehicles",
        icon: CarFront,
      },
    ],
  },

  {
    section: "BOOKING MANAGEMENT",
    items: [
      {
        label: "Bookings",
        path: "/bookings",
        icon: CalendarDays,
      },
      {
        label: "Assignments",
        path: "/assignments",
        icon: MapPinned,
      },
    ],
  },

  {
    section: "FINANCIAL MANAGEMENT",
    items: [
      {
        label: "Payments",
        path: "/payments",
        icon: CreditCard,
      },
      {
        label: "Documents",
        path: "/documents",
        icon: FileText,
      },
    ],
  },

  {
    section: "SYSTEM",
    items: [
      {
        label: "Notifications",
        path: "/notifications",
        icon: Bell,
      },
      {
        label: "My Profile",
        path: "/profile",
        icon: UserRound,
      },
    ],
  },
];

function Sidebar() {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem("user");
    navigate("/login");
  };

  return (
    <aside className="fixed left-0 top-0 flex h-screen w-64 flex-col bg-slate-950 text-white shadow-2xl">

      {/* Logo */}
      <div className="border-b border-slate-800 p-6">
        <div className="flex items-center gap-3">

          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-teal-500 font-bold text-slate-950">
            D
          </div>

          <div>
            <h1 className="text-xl font-bold text-white">
              DilaDrive
            </h1>

            <p className="text-xs text-slate-400">
              Transport Management
            </p>
          </div>

        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto px-3 py-4">

        {nav.map((group) => (
          <div key={group.section} className="mb-6">

            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-widest text-slate-500">
              {group.section}
            </p>

            <div className="space-y-1">

              {group.items.map(
                ({ label, path, icon: Icon }) => (
                  <NavLink
                    key={label}
                    to={path}
                    end={path === "/"}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-xl px-4 py-3 transition-all duration-200 ${
                        isActive
                          ? "bg-teal-500 font-semibold text-slate-950 shadow-md"
                          : "text-slate-300 hover:bg-slate-800 hover:text-white"
                      }`
                    }
                  >
                    <Icon size={18} />
                    <span>{label}</span>
                  </NavLink>
                )
              )}

            </div>
          </div>
        ))}

      </div>

      {/* Admin Card */}
      <div className="border-t border-slate-800 p-4">

        <div className="mb-4 rounded-2xl bg-slate-900 p-4">

          <div className="flex items-center gap-3">

            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-teal-500 font-bold text-slate-950">
              D
            </div>

            <div>
              <p className="text-sm font-semibold">
                DilaDrive Admin
              </p>

              <p className="text-xs text-slate-400">
                Administrator
              </p>
            </div>

          </div>

        </div>

        <button
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-xl px-4 py-3 text-red-300 transition hover:bg-red-500/10"
        >
          <LogOut size={18} />
          <span>Logout</span>
        </button>

        <p className="mt-4 text-center text-xs text-slate-500">
          Version 1.0
        </p>

      </div>

    </aside>
  );
}

export default Sidebar;