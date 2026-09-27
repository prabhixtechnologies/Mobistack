import { Link, useLocation } from "react-router-dom";
import { EmptyState } from "./EmptyState";

type Kind = "not-found" | "forbidden";

/**
 * A real dead-end page.
 *
 * <p>An unknown URL used to answer with `<Navigate to={home} replace />`. That hides the
 * problem three ways: the user cannot tell a typo from a stale link from a missing
 * permission, the `replace` throws away the history entry so Back cannot return them to
 * whatever sent them here, and a broken link that lands quietly on the dashboard is never
 * reported, so it stays broken.
 *
 * <p>The path is printed on purpose — it is the one detail that makes a report actionable.
 */
export function RouteError({ kind = "not-found" }: { kind?: Kind }) {
  const { pathname } = useLocation();
  const forbidden = kind === "forbidden";

  return (
    <div className="route-error">
      <EmptyState
        icon={forbidden ? "shield" : "search"}
        title={forbidden ? "You do not have access to that" : "That page is not here"}
        hint={
          forbidden
            ? "Your role does not include this area. A shop owner can grant the permission from Members."
            : "The link may be from an older build, or the record may have been removed. Nothing is wrong with your shop."
        }
        action={
          <>
            <code className="route-error__path">{pathname}</code>
            <div className="route-error__actions">
              <Link className="btn" to="/">
                Go to dashboard
              </Link>
              <button className="btn ghost" type="button" onClick={() => window.history.back()}>
                Back
              </button>
            </div>
          </>
        }
      />
    </div>
  );
}

export default RouteError;
