import { Link } from "react-router-dom";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { afterAuthPath } from "../lib/plan";
import { permissionLabel, type Permission } from "../lib/permissions";
import { Icon } from "../ui/navIcons";

/**
 * Shown when a route resolves but the principal lacks the capability for it.
 *
 * Names the missing permission and the current role so the user can tell their
 * owner exactly what to grant, rather than guessing at a generic "denied".
 */
export function ForbiddenPage({
  required,
  reason = "permission",
}: {
  required?: Permission;
  reason?: "permission" | "platform" | "unpaid";
}) {
  const { roleLabel } = useAccess();
  const { user } = useAuth();
  const unpaid = reason === "unpaid";
  const home = user ? afterAuthPath(user) : "/";

  return (
    <div className="page">
      <div className="forbidden">
        <div className="forbidden__icon">
          <Icon name="lock" />
        </div>
        <h1>{unpaid ? "This workspace is on hold" : "You don't have access to this"}</h1>

        {reason === "platform" && (
          <p className="muted">
            The platform console is limited to MobiStack staff accounts. Your workspace role is{" "}
            <strong>{roleLabel}</strong>.
          </p>
        )}
        {reason === "permission" && (
          <p className="muted">
            This screen needs the <strong>{required ? permissionLabel(required) : "required"}</strong> permission.
            You're signed in as <strong>{roleLabel}</strong>, which doesn't include it.
          </p>
        )}
        {unpaid && (
          <p className="muted">
            The counter is locked until the subscription is renewed. Only an owner can complete payment, and
            your role is <strong>{roleLabel}</strong>.
          </p>
        )}

        <p className="faint">
          {unpaid
            ? "Ask a workspace owner to open Billing and reactivate the subscription."
            : "Ask a workspace owner or admin to update your role under People."}
        </p>

        <div className="row forbidden__actions">
          {!unpaid && (
            <Link className="btn" to={home}>
              Back to dashboard
            </Link>
          )}
          <Link className={unpaid ? "btn" : "btn ghost"} to="/support">
            Contact support
          </Link>
        </div>
      </div>
    </div>
  );
}
