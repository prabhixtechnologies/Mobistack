import { Suspense, lazy, type ComponentType, type LazyExoticComponent } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { selectedWorkspaceId } from "./lib/types";
import { AppShell } from "./ui/AppShell";
import { BrandMark } from "./ui/BrandMark";
import { ThemeToggle } from "./ui/ThemeToggle";
import { RequirePermission, RequirePlatformAdmin } from "./ui/PermissionGate";
import { LoginPage } from "./pages/LoginPage";
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

const DashboardPage = page(() => import("./pages/DashboardPage"), "DashboardPage");
const SearchPage = page(() => import("./pages/SearchPage"), "SearchPage");
const DevicePage = page(() => import("./pages/DevicePage"), "DevicePage");
const InventoryPage = page(() => import("./pages/InventoryPage"), "InventoryPage");
const CompatibilityPage = page(() => import("./pages/CompatibilityPage"), "CompatibilityPage");
const MovementsPage = page(() => import("./pages/MovementsPage"), "MovementsPage");
const SettingsPage = page(() => import("./pages/SettingsPage"), "SettingsPage");
const SalesPage = page(() => import("./pages/SalesPage"), "SalesPage");
const RepairsPage = page(() => import("./pages/RepairsPage"), "RepairsPage");
const CustomersPage = page(() => import("./pages/CustomersPage"), "CustomersPage");
const SuppliersPage = page(() => import("./pages/SuppliersPage"), "SuppliersPage");
const PurchasesPage = page(() => import("./pages/PurchasesPage"), "PurchasesPage");
const ReportsPage = page(() => import("./pages/ReportsPage"), "ReportsPage");
const MembersPage = page(() => import("./pages/MembersPage"), "MembersPage");
const AccessControlPage = page(() => import("./pages/AccessControlPage"), "AccessControlPage");
const ProfilePage = page(() => import("./pages/ProfilePage"), "ProfilePage");
const SystemHealthPage = page(() => import("./pages/SystemHealthPage"), "SystemHealthPage");
const BillingPage = page(() => import("./pages/BillingPage"), "BillingPage");
const ImportPage = page(() => import("./pages/ImportPage"), "ImportPage");
const AuditPage = page(() => import("./pages/AuditPage"), "AuditPage");
const AdminPage = page(() => import("./pages/AdminPage"), "AdminPage");
const NotificationsPage = page(() => import("./pages/NotificationsPage"), "NotificationsPage");
const SupportPage = page(() => import("./pages/SupportPage"), "SupportPage");
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
      <Route path="/app/:platform" element={<AppDownloadPage />} />
    </>
  );
}

function UnscopedWorkspaces() {
  const { logout } = useAuth();
  return (
    <div>
      <header className="app-header">
        <BrandMark compact inverse />
        <div className="app-header__actions">
          <ThemeToggle icon />
          <button className="btn ghost header-ghost" type="button" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>
      <WorkspacesPage />
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
  const { user } = useAuth();

  if (!user) {
    return (
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
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
            <Route path="/" element={<DashboardPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/devices/:id" element={<DevicePage />} />
            <Route path="/sales" element={<SalesPage />} />
            <Route path="/repairs" element={<RepairsPage />} />
            <Route path="/inventory" element={<InventoryPage />} />
            <Route path="/purchases" element={<PurchasesPage />} />
            <Route path="/customers" element={<CustomersPage />} />
            <Route path="/suppliers" element={<SuppliersPage />} />
            <Route path="/compatibility" element={<CompatibilityPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/movements" element={<MovementsPage />} />
            <Route path="/members" element={<MembersPage />} />
            <Route path="/users" element={<AccessControlPage />} />
            <Route path="/workspaces" element={<WorkspacesPage />} />
            <Route path="/billing" element={<BillingPage />} />
            <Route path="/import" element={<ImportPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/support" element={<SupportPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/health" element={<SystemHealthPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Route>
          <Route element={<RequirePlatformAdmin />}>
            <Route path="/admin" element={<AdminPage />} />
          </Route>
        </Route>
        {LegalRoutes()}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
