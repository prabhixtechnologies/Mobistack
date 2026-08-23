import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { NAV_SECTIONS, type NavItem } from "../lib/navigation";
import { selectedWorkspaceId } from "../lib/types";
import { api } from "../lib/api";
import { usePresence } from "../lib/presence";
import { BrandFooter, BrandMark } from "./BrandMark";
import { ThemeToggle } from "./ThemeToggle";
import { GlobalSearch } from "./GlobalSearch";
import { Breadcrumbs } from "./Breadcrumbs";
import { Menu, MenuItem, MenuSeparator } from "./Menu";
import { ErrorBoundary } from "./ErrorBoundary";
import { Icon } from "./navIcons";

const SIDEBAR_KEY = "mobistack.sidebar.collapsed";

/** Non-production deployments get a badge so nobody edits live data by mistake. */
const ENVIRONMENT = (import.meta.env.VITE_ENVIRONMENT as string | undefined)?.toUpperCase();

function initials(name?: string): string {
  const parts = (name ?? "U").trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function AppShell() {
  const { user, workspaces, logout, switchWorkspace } = useAuth();
  const access = useAccess();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const currentWorkspace = selectedWorkspaceId(user);
  const activeWorkspaces = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const unpaid = Boolean(user?.paymentRequired);

  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === "1");
  const [unread, setUnread] = useState(0);
  usePresence(Boolean(user));

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, collapsed ? "1" : "0");
  }, [collapsed]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "b") {
        event.preventDefault();
        setCollapsed((value) => !value);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

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

  /**
   * A nav entry survives when the viewer holds its capability, it clears the
   * platform-staff bar, and an unpaid workspace hasn't locked it away. Sections
   * that end up empty are dropped so no bare heading is left behind.
   */
  const visible = (item: NavItem): boolean => {
    if (item.platformAdmin) {
      return access.isPlatformAdmin && !unpaid;
    }
    if (unpaid && !item.allowUnpaid) {
      return false;
    }
    return !item.need || access.has(item.need);
  };

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(visible),
  })).filter((section) => section.items.length > 0);

  return (
    <div className={`app-shell${collapsed ? " app-shell--collapsed" : ""}`}>
      <header className="app-header" role="banner">
        <button
          className="icon-btn header-icon sidebar-toggle"
          type="button"
          onClick={() => setCollapsed((value) => !value)}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          title="Toggle sidebar (Ctrl+B)"
        >
          <Icon name="panelLeft" />
        </button>

        <BrandMark compact inverse />

        {ENVIRONMENT && ENVIRONMENT !== "PRODUCTION" && (
          <span className="env-badge" title="You are not on production">
            {ENVIRONMENT}
          </span>
        )}

        <GlobalSearch />

        <div className="app-header__actions">
          {activeWorkspaces.length > 1 && (
            <select
              className="select workspace-switcher header-select"
              value={currentWorkspace ?? ""}
              aria-label="Switch workspace"
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

          <Menu
            label="Account menu"
            trigger={({ open }) => (
              <span className={`user-chip${open ? " user-chip--open" : ""}`}>
                <span className="avatar">{initials(user?.fullName)}</span>
                <span className="user-chip__text">
                  <strong>{user?.fullName}</strong>
                  <small>{access.roleLabel}</small>
                </span>
                <Icon name="chevronDown" />
              </span>
            )}
          >
            {(close) => (
              <>
                <div className="menu__header">
                  <strong>{user?.fullName}</strong>
                  <span className="muted">{user?.email}</span>
                  <span className={`badge role-badge tint-${access.roleTint}`}>{access.roleLabel}</span>
                  {access.isPlatformAdmin && <span className="badge neutral">Platform staff</span>}
                </div>
                <MenuSeparator />
                <MenuItem
                  icon={<Icon name="user" />}
                  onClick={() => {
                    close();
                    navigate("/profile");
                  }}
                >
                  My profile
                </MenuItem>
                <MenuItem
                  icon={<Icon name="settings" />}
                  onClick={() => {
                    close();
                    navigate("/settings");
                  }}
                >
                  Workspace settings
                </MenuItem>
                <MenuItem
                  icon={<Icon name="grid" />}
                  onClick={() => {
                    close();
                    navigate("/workspaces");
                  }}
                >
                  Switch workspace
                </MenuItem>
                <MenuSeparator />
                <MenuItem
                  danger
                  icon={<Icon name="logout" />}
                  onClick={async () => {
                    close();
                    await logout();
                    navigate("/login");
                  }}
                >
                  Sign out
                </MenuItem>
              </>
            )}
          </Menu>
        </div>
      </header>

      <div className="app-body">
        <aside className="sidebar" aria-label="Main navigation">
          <div className="sidebar-workspace">
            <strong>{user?.workspaceName ?? user?.shopName ?? "Workspace"}</strong>
            <span>{access.roleLabel}</span>
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
                    title={item.label}
                    className={({ isActive }) => `nav-link tint-${item.tint}${isActive ? " active" : ""}`}
                  >
                    <span className="nav-ico">
                      <Icon name={item.icon} />
                    </span>
                    <span className="nav-link__label">{item.label}</span>
                    {item.to === "/notifications" && unread > 0 && <span className="nav-badge">{unread}</span>}
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
              <span className="nav-link__label">Sign out</span>
            </button>
          </div>
        </aside>

        <div className="main" id="main-content">
          {unpaid && (
            <div className="banner banner-warn paywall-strip">
              Payment pending — finish Billing to unlock the counter.
            </div>
          )}
          <div className="main__crumbs">
            <Breadcrumbs />
          </div>
          <ErrorBoundary resetKey={pathname}>
            <Outlet />
          </ErrorBoundary>
          <div className="main__footer">
            <BrandFooter />
          </div>
        </div>
      </div>

      <NavLink to="/support" className="support-fab" aria-label="Open support">
        <Icon name="chat" />
      </NavLink>
    </div>
  );
}
