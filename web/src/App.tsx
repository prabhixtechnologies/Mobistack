import { Suspense, lazy, type ComponentType, type LazyExoticComponent } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { selectedWorkspaceId } from "./lib/types";
import { AppShell } from "./ui/AppShell";
import { BrandFooter, BrandMark } from "./ui/BrandMark";
import { ThemeToggle } from "./ui/ThemeToggle";
import { SkipLink } from "./ui/SkipLink";
import { RequirePermission } from "./ui/PermissionGate";
import { LoginPage, SessionRestore } from "./pages/LoginPage";
import { OidcCallbackPage } from "./pages/OidcCallbackPage";
import { WorkspacesPage } from "./pages/WorkspacesPage";

/**
 * Named-export pages need adapting before `React.lazy`, which expects a module
 * with a `default`. Keeping the helper here means pages stay conventional named
 * exports and testable without a default-export shim.
 *
 * Only the named export is constrained, so a module may export other things, and
 * the page's own prop types survive the indirection — `LegalPage` still requires
 * its `kind`.
 */
function page<P extends object, K extends string>(
  loader: () => Promise<{ [key in K]: ComponentType<P> }>,
  name: K,
): LazyExoticComponent<ComponentType<P>> {
  return lazy(() => loader().then((module) => ({ default: module[name] })));
}

const CommonsBrowsePage = page(() => import("./pages/CommonsBrowsePage"), "CommonsBrowsePage");
const CommonsDevicePage = page(() => import("./pages/CommonsDevicePage"), "CommonsDevicePage");
const CommonsComponentPage = page(() => import("./pages/CommonsComponentPage"), "CommonsComponentPage");
const BillingPage = page(() => import("./pages/BillingPage"), "BillingPage");
const LegalPage = page(() => import("./pages/LegalPage"), "LegalPage");
const AppDownloadPage = page(() => import("./pages/AppDownloadPage"), "AppDownloadPage");
const ForcePasswordChangePage = page(
  () => import("./pages/ForcePasswordChangePage"),
  "ForcePasswordChangePage",
);

function RouteFallback() {
  return (
    <div className="page route-fallback" aria-busy="true" aria-live="polite">
      <div className="skeleton skeleton--title" />
      <div className="grid-4">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="card" key={index}>
            <div className="skeleton" />
            <div className="skeleton skeleton--value" />
          </div>
        ))}
      </div>
      <div className="card">
        <div className="skeleton" />
        <div className="skeleton" />
        <div className="skeleton" />
      </div>
    </div>
  );
}

function LegalRoutes() {
  return (
    <>
      <Route path="/privacy" element={<LegalPage kind="privacy" />} />
      <Route path="/terms" element={<LegalPage kind="terms" />} />
      <Route path="/refunds" element={<LegalPage kind="refunds" />} />
      <Route path="/app" element={<AppDownloadPage />} />
      <Route path="/app/:platform" element={<AppDownloadPage />} />
    </>
  );
}

function UnscopedWorkspaces() {
  const { logout } = useAuth();
  return (
    <div className="workspaces-shell">
      <SkipLink />
      <header className="app-header">
        <BrandMark compact />
        <div className="app-header__actions">
          <ThemeToggle icon />
          <button className="btn ghost header-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>
      <div className="workspaces-shell__body" id="main-content" tabIndex={-1}>
        <WorkspacesPage />
      </div>
      <BrandFooter />
    </div>
  );
}

/**
 * Gates the app in widening order: session, then a forced password change, then a
 * selected workspace, then subscription state, and finally per-route capability.
 * Each stage assumes the previous one passed, so a route only ever needs to
 * declare its permission.
 */
export function App() {
  const { user, ready } = useAuth();

  if (!ready) {
    return (
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/auth/callback" element={<OidcCallbackPage />} />
          {LegalRoutes()}
          <Route path="*" element={<SessionRestore />} />
        </Routes>
      </Suspense>
    );
  }

  if (!user) {
    return (
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<OidcCallbackPage />} />
          {LegalRoutes()}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Suspense>
    );
  }

  // A temporary credential must be replaced before it can reach workspace data.
  if (user.mustChangePassword) {
    return (
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/change-password" element={<ForcePasswordChangePage />} />
          {LegalRoutes()}
          <Route path="*" element={<Navigate to="/change-password" replace />} />
        </Routes>
      </Suspense>
    );
  }

  if (!selectedWorkspaceId(user)) {
    return (
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/workspaces" element={<UnscopedWorkspaces />} />
          {LegalRoutes()}
          <Route path="*" element={<Navigate to="/workspaces" replace />} />
        </Routes>
      </Suspense>
    );
  }

  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route element={<AppShell />}>
          {/* Capability comes from ROUTE_PERMISSIONS, keyed on the live pathname. */}
          <Route element={<RequirePermission />}>
            <Route path="/" element={<Navigate to="/commons" replace />} />
            <Route path="/commons" element={<CommonsBrowsePage />} />
            <Route path="/commons/devices/:id" element={<CommonsDevicePage />} />
            <Route path="/commons/components/:id" element={<CommonsComponentPage />} />
            <Route path="/billing" element={<BillingPage />} />
            <Route path="*" element={<Navigate to="/commons" replace />} />
          </Route>
        </Route>
        {LegalRoutes()}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
