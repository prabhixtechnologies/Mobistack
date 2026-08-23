import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { selectedWorkspaceId } from "../lib/types";
import { BrandMark } from "./BrandMark";
import { ThemeToggle } from "./ThemeToggle";
import { GlobalSearch } from "./GlobalSearch";
import { usePresence } from "../lib/presence";
import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { Icon, type NavIconName } from "./navIcons";

type Tint = "rose" | "green" | "amber" | "blue" | "violet" | "cyan" | "slate" | "orange";

interface NavItem {
  to: string;
  label: string;
  icon: NavIconName;
  tint: Tint;
  end?: boolean;
}

const NAV: { label: string; items: NavItem[] }[] = [
  {
    label: "Counter",
    items: [
      { to: "/", label: "Dashboard", icon: "home", tint: "rose", end: true },
      { to: "/sales", label: "Sales", icon: "cart", tint: "green" },
      { to: "/repairs", label: "Repairs", icon: "wrench", tint: "amber" },
      { to: "/inventory", label: "Inventory", icon: "box", tint: "blue" },
      { to: "/purchases", label: "Purchases", icon: "truck", tint: "orange" },
    ],
  },
  {
    label: "People",
    items: [
      { to: "/customers", label: "Customers", icon: "users", tint: "violet" },
      { to: "/suppliers", label: "Suppliers", icon: "store", tint: "cyan" },
      { to: "/members", label: "People", icon: "people", tint: "blue" },
    ],
  },
  {
    label: "Catalog",
    items: [
      { to: "/compatibility", label: "Compatibility", icon: "link", tint: "violet" },
      { to: "/import", label: "Import", icon: "upload", tint: "slate" },
    ],
  },
  {
    label: "Insights",
    items: [
      { to: "/reports", label: "Reports", icon: "chart", tint: "green" },
      { to: "/movements", label: "Movements", icon: "move", tint: "amber" },
      { to: "/audit", label: "Audit", icon: "shield", tint: "slate" },
    ],
  },
  {
    label: "Workspace",
    items: [
      { to: "/workspaces", label: "Workspaces", icon: "grid", tint: "violet" },
      { to: "/billing", label: "Billing", icon: "card", tint: "green" },
      { to: "/settings", label: "Settings", icon: "settings", tint: "slate" },
    ],
  },
  {
    label: "Support",
    items: [
      { to: "/notifications", label: "Notifications", icon: "bell", tint: "rose" },
      { to: "/support", label: "Support", icon: "chat", tint: "violet" },
    ],
  },
];

function initials(name?: string): string {
  const parts = (name ?? "U").trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function AppShell() {
  const { user, workspaces, logout, switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const currentWorkspace = selectedWorkspaceId(user);
  const activeWorkspaces = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const allowedWhenUnpaid = new Set(["/billing", "/settings", "/notifications", "/support", "/workspaces"]);
  const baseSections = user?.paymentRequired
    ? NAV.map((section) => ({
        ...section,
        items: section.items.filter((item) => allowedWhenUnpaid.has(item.to)),
      })).filter((section) => section.items.length > 0)
    : NAV;
  const sections = user?.systemAdmin && !user.paymentRequired
    ? [...baseSections, { label: "Platform", items: [{ to: "/admin", label: "Platform", icon: "crown" as const, tint: "amber" as const }] }]
    : baseSections;
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
      <header className="app-header">
        <BrandMark compact inverse />
        <span className="role-chip">{user?.roles[0] ?? "MEMBER"}</span>
        <GlobalSearch />
        <div className="app-header__actions">
          {activeWorkspaces.length > 1 && (
            <select
              className="select workspace-switcher header-select"
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
          <ThemeToggle icon />
          <button
            className="icon-btn header-icon"
            type="button"
            aria-label={unread ? `${unread} unread notifications` : "Notifications"}
            onClick={() => navigate("/notifications")}
          >
            <Icon name="bell" />
            {unread > 0 && <span className="header-dot">{unread > 9 ? "9+" : unread}</span>}
          </button>
          <div className="avatar" title={user?.email}>
            {initials(user?.fullName)}
          </div>
        </div>
      </header>

      <div className="app-body">
        <aside className="sidebar">
          <div className="sidebar-workspace">
            <strong>{user?.workspaceName ?? user?.shopName ?? "Workspace"}</strong>
            <span>{user?.fullName}</span>
          </div>
          <nav className="nav-group">
            {sections.map((section) => (
              <div className="nav-section" key={section.label}>
                <div className="nav-section__label">{section.label}</div>
                {section.items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `nav-link tint-${item.tint}${isActive ? " active" : ""}`
                    }
                  >
                    <span className="nav-ico">
                      <Icon name={item.icon} />
                    </span>
                    <span>{item.label}</span>
                    {item.to === "/notifications" && unread > 0 && (
                      <span className="nav-badge">{unread}</span>
                    )}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>
          <div className="sidebar-foot">
            <button
              className="nav-link tint-slate"
              type="button"
              onClick={async () => {
                await logout();
                navigate("/login");
              }}
            >
              <span className="nav-ico">
                <Icon name="logout" />
              </span>
              <span>Sign out</span>
            </button>
          </div>
        </aside>

        <div className="main">
          {user?.paymentRequired && (
            <div className="banner banner-warn paywall-strip">
              Payment pending — finish Billing to unlock the counter.
            </div>
          )}
          <Outlet />
        </div>
      </div>

      <NavLink to="/support" className="support-fab" aria-label="Open support">
        <Icon name="chat" />
      </NavLink>
    </div>
  );
}
