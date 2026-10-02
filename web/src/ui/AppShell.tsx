import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { DRAWER_QUERY, TABLET_QUERY, useMediaQuery } from "../lib/media";
import { NAV_SECTIONS, routeContext, type NavItem } from "../lib/navigation";
import { selectedWorkspaceId } from "../lib/types";
import { api } from "../lib/api";
import { usePresence } from "../lib/presence";
import { GlobalSearch, openCommandPalette } from "./GlobalSearch";
import { BrandFooter, BrandMark } from "./BrandMark";
import { ThemeToggle } from "./ThemeToggle";
import { Breadcrumbs } from "./Breadcrumbs";
import { Menu, MenuItem, MenuSeparator } from "./Menu";
import { ErrorBoundary } from "./ErrorBoundary";
import { Icon } from "./navIcons";
import { SkipLink } from "./SkipLink";
import { Modal } from "./Modal";

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

function isTextEntry(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  return Boolean(element?.closest("input, textarea, select, [contenteditable='true'], [role='dialog']"));
}

export function AppShell() {
  const { user, workspaces, logout, switchWorkspace, refreshUser } = useAuth();
  const access = useAccess();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const currentWorkspace = selectedWorkspaceId(user);
  const activeWorkspaces = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const unpaid = Boolean(user?.paymentRequired);
  const features = user?.features ?? [];

  const drawer = useMediaQuery(DRAWER_QUERY);
  const tablet = useMediaQuery(TABLET_QUERY);
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window !== "undefined" && window.matchMedia("(max-width: 1099.98px)").matches) {
      return true;
    }
    return localStorage.getItem(SIDEBAR_KEY) === "1";
  });
  const [navOpen, setNavOpen] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const [unlockError, setUnlockError] = useState<string | null>(null);
  const [shortcutOpen, setShortcutOpen] = useState(false);
  const [online, setOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));
  const goChord = useRef(false);
  const goTimer = useRef<number | null>(null);
  // Seat enforcement was switched off here while Settings went on reporting seat counts
  // as if it were running, so the numbers a shop owner saw were not the numbers the
  // backend held. Heartbeat only once there is a signed-in user in a workspace: an
  // anonymous or workspace-less shell has no seat to claim.
  usePresence(Boolean(user && currentWorkspace));
  const localUnlock = Boolean(user?.localActivationAvailable);
  const canBill = access.has("WORKSPACE_BILLING");
  const canSales = access.has("SALES_READ") && features.includes("SALES");
  const context = routeContext(pathname);

  async function activateLocalShop() {
    setUnlocking(true);
    setUnlockError(null);
    try {
      await api("/api/v1/mobistack/billing/dev/activate", { method: "POST" });
      await refreshUser();
    } catch (cause) {
      setUnlockError(cause instanceof Error ? cause.message : "Could not activate the local shop.");
    } finally {
      setUnlocking(false);
    }
  }

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, collapsed ? "1" : "0");
  }, [collapsed]);

  useEffect(() => {
    if (drawer) {
      setNavOpen(false);
      return;
    }
    if (tablet) {
      setCollapsed(true);
    }
  }, [drawer, tablet]);

  useEffect(() => {
    setNavOpen(false);
  }, [pathname]);

  useEffect(() => {
    document.title = `${context.title} · MobiStack`;
  }, [context.title]);

  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && navOpen) {
        event.preventDefault();
        setNavOpen(false);
        return;
      }
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "b") {
        event.preventDefault();
        if (drawer) {
          setNavOpen((value) => !value);
        } else {
          setCollapsed((value) => !value);
        }
        return;
      }
      if (isTextEntry(event.target)) return;
      if (event.key === "?") {
        event.preventDefault();
        setShortcutOpen(true);
        return;
      }
      if (event.key === "/") {
        event.preventDefault();
        openCommandPalette();
        return;
      }
      if (event.key.toLowerCase() === "g") {
        goChord.current = true;
        if (goTimer.current !== null) window.clearTimeout(goTimer.current);
        goTimer.current = window.setTimeout(() => {
          goChord.current = false;
        }, 900);
        return;
      }
      if (goChord.current) {
        const routes: Record<string, string> = {
          h: "/",
          s: "/sales",
          r: "/repairs",
          i: "/inventory",
          c: "/commons",
        };
        const route = routes[event.key.toLowerCase()];
        goChord.current = false;
        if (route) {
          event.preventDefault();
          navigate(route);
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      if (goTimer.current !== null) window.clearTimeout(goTimer.current);
    };
  }, [drawer, navOpen, navigate]);

  /**
   * A nav entry survives when the shop's plan includes it, the viewer holds its
   * capability, and an unpaid workspace hasn't locked it away. A platform admin
   * follows the same plan inside the shop. Empty sections are dropped.
   */
  const visible = (item: NavItem): boolean => {
    if (item.platformAdmin) {
      return access.isPlatformAdmin && !unpaid;
    }
    if (unpaid && !item.allowUnpaid) {
      return false;
    }
    if (item.commonsReviewer && !user?.commonsReviewer) {
      return false;
    }
    if (item.feature && !features.includes(item.feature)) {
      return false;
    }
    return !item.need || access.has(item.need);
  };

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(visible),
  })).filter((section) => section.items.length > 0);

  const shellClass = [
    "app-shell",
    drawer ? "app-shell--drawer" : collapsed ? "app-shell--collapsed" : "",
    drawer && navOpen ? "app-shell--nav-open" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const menuOpen = drawer ? navOpen : !collapsed;

  return (
    <div className={shellClass}>
      <SkipLink />
      <header className="app-header" role="banner">
        <button
          className="icon-btn header-icon sidebar-toggle"
          type="button"
          onClick={() => (drawer ? setNavOpen((value) => !value) : setCollapsed((value) => !value))}
          aria-expanded={menuOpen}
          aria-controls="app-sidebar"
          aria-label={drawer ? (navOpen ? "Close menu" : "Open menu") : collapsed ? "Expand sidebar" : "Collapse sidebar"}
          title={drawer ? "Menu" : "Toggle sidebar (Ctrl+B)"}
        >
          <Icon name="panelLeft" />
        </button>

        <BrandMark compact />

        {ENVIRONMENT && ENVIRONMENT !== "PRODUCTION" && (
          <span className="env-badge" title="You are not on production">
            {ENVIRONMENT}
          </span>
        )}

        {/*
          The trigger is a flex item. It has to live in this horizontal header. As a child of
          the column shell, flex-grow stretches it down the viewport and the page starts below it.
        */}
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
                {access.has("SETTINGS_READ") && (
                  <MenuItem
                    icon={<Icon name="settings" />}
                    onClick={() => {
                      close();
                      navigate("/settings");
                    }}
                  >
                    Shop settings
                  </MenuItem>
                )}
                {canBill && (
                  <MenuItem
                    icon={<Icon name="card" />}
                    onClick={() => {
                      close();
                      navigate("/billing");
                    }}
                  >
                    Billing
                  </MenuItem>
                )}
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
        {drawer && (
          <button
            className="sidebar-scrim"
            type="button"
            tabIndex={navOpen ? 0 : -1}
            aria-label="Close menu"
            onClick={() => setNavOpen(false)}
          />
        )}
        <aside
          className="sidebar"
          id="app-sidebar"
          aria-label="Main navigation"
          aria-hidden={drawer && !navOpen}
          inert={drawer && !navOpen ? true : undefined}
        >
          <div className="sidebar-product">
            <div className="sidebar-product__name">MobiStack</div>
            <div className="sidebar-product__tag">Shop operations</div>
          </div>
          <div className="sidebar-workspace">
            <span className="sidebar-workspace__mark" aria-hidden>
              {(user?.workspaceName ?? user?.shopName ?? "W").trim().charAt(0).toUpperCase()}
            </span>
            <div>
              <strong>{user?.workspaceName ?? user?.shopName ?? "Workspace"}</strong>
              <span>{access.roleLabel}</span>
            </div>
          </div>

          <nav className="nav-group">
            {sections.map((section) => (
              <div className="nav-section" key={section.label}>
                <div className="nav-section__label">
                  <span>{section.label}</span>
                  {section.hint && <small>{section.hint}</small>}
                </div>
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

        <div className="main" id="main-content" tabIndex={-1}>
          <div className="main__body">
            {!online && (
              <div className="offline-bar" role="status">
                <Icon name="alert" />
                <div>
                  <strong>You’re offline</strong>
                  <span>Read what is already open. New changes will be available after the connection returns.</span>
                </div>
              </div>
            )}
            {unpaid && (
              <div className="lock-bar">
                <div>
                  <strong>Shop features are locked</strong>
                  <p>
                    The shared catalog stays open. Dashboard, stock, sales, and private fitment notes
                    need a live plan.
                    {unlockError ? ` ${unlockError}` : ""}
                  </p>
                </div>
                <div className="lock-bar__actions">
                  {localUnlock && canBill && (
                    <button className="btn" type="button" disabled={unlocking} onClick={() => void activateLocalShop()}>
                      {unlocking ? "Turning on…" : "Turn on local shop"}
                    </button>
                  )}
                  {canBill && (
                    <Link className="btn ghost" to="/billing">
                      Open billing
                    </Link>
                  )}
                </div>
              </div>
            )}
            {pathname !== "/" && (
              <div className="main__crumbs">
                <Breadcrumbs />
              </div>
            )}
            <ErrorBoundary resetKey={pathname}>
              <Outlet />
            </ErrorBoundary>
          </div>
        </div>
      </div>

      <BrandFooter />

      {drawer && (
        <nav className="app-dock" aria-label="Primary shop actions">
          <NavLink to="/" end className={({ isActive }) => `app-dock__item${isActive ? " active" : ""}`}>
            <Icon name="home" />
            Home
          </NavLink>
          <NavLink
            to={canSales ? "/sales" : "/commons"}
            className={({ isActive }) => `app-dock__item${isActive ? " active" : ""}`}
          >
            <Icon name={canSales ? "cart" : "globe"} />
            {canSales ? "Sale" : "Catalog"}
          </NavLink>
          <button className="app-dock__item" type="button" onClick={() => openCommandPalette()}>
            <Icon name="search" />
            Scan
          </button>
          <button
            className="app-dock__item"
            type="button"
            onClick={() => setNavOpen((value) => !value)}
            aria-expanded={navOpen}
            aria-controls="app-sidebar"
          >
            <Icon name="panelLeft" />
            More
          </button>
        </nav>
      )}

      <Modal
        open={shortcutOpen}
        size="sm"
        title="Keyboard shortcuts"
        description="Move around the counter without leaving the keyboard."
        onClose={() => setShortcutOpen(false)}
      >
        <dl className="shortcut-list">
          <div><dt><kbd>Ctrl K</kbd></dt><dd>Search, scan, or jump to a page</dd></div>
          <div><dt><kbd>/</kbd></dt><dd>Open search</dd></div>
          <div><dt><kbd>G H</kbd></dt><dd>Go to the shop floor</dd></div>
          <div><dt><kbd>G S</kbd></dt><dd>Go to Sales</dd></div>
          <div><dt><kbd>G R</kbd></dt><dd>Go to Repairs</dd></div>
          <div><dt><kbd>G I</kbd></dt><dd>Go to Inventory</dd></div>
          <div><dt><kbd>G C</kbd></dt><dd>Go to Shared fitment</dd></div>
          <div><dt><kbd>Ctrl B</kbd></dt><dd>Toggle navigation</dd></div>
          <div><dt><kbd>?</kbd></dt><dd>Show this guide</dd></div>
        </dl>
      </Modal>
    </div>
  );
}
