import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { selectedWorkspaceId } from "../lib/types";
import { BrandFooter, BrandMark } from "./BrandMark";
import { ThemeToggle } from "./ThemeToggle";
import { GlobalSearch } from "./GlobalSearch";
import { usePresence } from "../lib/presence";
import { useEffect, useState } from "react";
import { api } from "../lib/api";

const LINKS = [
  { to: "/", label: "Dashboard" },
  { to: "/sales", label: "Sales" },
  { to: "/repairs", label: "Repairs" },
  { to: "/inventory", label: "Inventory" },
  { to: "/purchases", label: "Purchases" },
  { to: "/customers", label: "Customers" },
  { to: "/suppliers", label: "Suppliers" },
  { to: "/compatibility", label: "Compatibility" },
  { to: "/reports", label: "Reports" },
  { to: "/movements", label: "Movements" },
  { to: "/members", label: "People" },
  { to: "/workspaces", label: "Workspaces" },
  { to: "/billing", label: "Billing" },
  { to: "/import", label: "Import" },
  { to: "/notifications", label: "Notifications" },
  { to: "/support", label: "Support" },
  { to: "/audit", label: "Audit" },
  { to: "/settings", label: "Settings" },
];

export function AppShell() {
  const { user, workspaces, logout, switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const currentWorkspace = selectedWorkspaceId(user);
  const activeWorkspaces = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const links = user?.systemAdmin ? [...LINKS, { to: "/admin", label: "Platform" }] : LINKS;
  const [unread, setUnread] = useState(0);
  usePresence(Boolean(user));

  useEffect(() => {
    if (!user) {
      return;
    }
    const pull = () => {
      void api<{ unread: number }>("/api/v1/inbox")
        .then((inbox) => setUnread(inbox.unread))
        .catch(() => undefined);
    };
    pull();
    const timer = window.setInterval(pull, 20000);
    return () => window.clearInterval(timer);
  }, [user]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <BrandMark />
        <nav className="nav-group">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === "/"}
              className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
            >
              <span>{link.label}</span>
              {link.to === "/notifications" && unread > 0 && <span className="nav-badge">{unread}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot stack">
          <ThemeToggle />
          <button
            className="btn ghost"
            type="button"
            onClick={async () => {
              await logout();
              navigate("/login");
            }}
          >
            Sign out
          </button>
          <BrandFooter />
        </div>
      </aside>
      <div className="main">
        <header className="topbar">
          <GlobalSearch />
          <div className="spread" style={{ minWidth: 280 }}>
            <div>
              <div style={{ fontWeight: 600 }}>{user?.fullName}</div>
              <div className="faint" style={{ fontSize: 12 }}>
                {user?.workspaceName ?? user?.shopName} · {user?.roles[0]}
              </div>
            </div>
            {activeWorkspaces.length > 1 && (
              <select
                className="select workspace-switcher"
                value={currentWorkspace ?? ""}
                onChange={(event) => {
                  const next = event.target.value;
                  if (next && next !== currentWorkspace) {
                    void switchWorkspace(next);
                  }
                }}
              >
                {activeWorkspaces.map((workspace) => (
                  <option key={workspace.id} value={workspace.id}>
                    {workspace.name}
                  </option>
                ))}
              </select>
            )}
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
