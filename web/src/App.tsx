import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { selectedWorkspaceId } from "./lib/types";
import { AppShell } from "./ui/AppShell";
import { BrandMark } from "./ui/BrandMark";
import { ThemeToggle } from "./ui/ThemeToggle";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { SearchPage } from "./pages/SearchPage";
import { DevicePage } from "./pages/DevicePage";
import { InventoryPage } from "./pages/InventoryPage";
import { CompatibilityPage } from "./pages/CompatibilityPage";
import { MovementsPage } from "./pages/MovementsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { WorkspacesPage } from "./pages/WorkspacesPage";
import { SalesPage } from "./pages/SalesPage";
import { RepairsPage } from "./pages/RepairsPage";
import { CustomersPage } from "./pages/CustomersPage";
import { SuppliersPage } from "./pages/SuppliersPage";
import { PurchasesPage } from "./pages/PurchasesPage";
import { ReportsPage } from "./pages/ReportsPage";
import { MembersPage } from "./pages/MembersPage";
import { BillingPage } from "./pages/BillingPage";
import { ImportPage } from "./pages/ImportPage";
import { AuditPage } from "./pages/AuditPage";
import { AdminPage } from "./pages/AdminPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { SupportPage } from "./pages/SupportPage";
import { AppDownloadPage } from "./pages/AppDownloadPage";

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

export function App() {
  const { user } = useAuth();

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/app/:platform" element={<AppDownloadPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  if (!selectedWorkspaceId(user)) {
    return (
      <Routes>
        <Route path="/workspaces" element={<UnscopedWorkspaces />} />
        <Route path="*" element={<Navigate to="/workspaces" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
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
        <Route path="/workspaces" element={<WorkspacesPage />} />
        <Route path="/billing" element={<BillingPage />} />
        <Route path="/import" element={<ImportPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/support" element={<SupportPage />} />
        <Route path="/audit" element={<AuditPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Route>
      <Route path="/app/:platform" element={<AppDownloadPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
