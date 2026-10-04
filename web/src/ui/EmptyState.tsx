import type { ReactNode } from "react";
import { Icon, type NavIconName } from "./navIcons";

/**
 * The "nothing here yet" surface.
 *
 * Always takes a `hint` so the user learns why the list is empty and what to do
 * about it — an unexplained blank panel reads as a bug.
 */
export function EmptyState({
  icon = "box",
  title,
  hint,
  action,
  compact = false,
}: {
  icon?: NavIconName;
  title: string;
  hint?: string;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={`empty-state${compact ? " empty-state--compact" : ""}`}>
      <div className="empty-state__icon">
        <Icon name={icon} />
      </div>
      <h2 className="empty-state__title">{title}</h2>
      {hint && <p className="muted">{hint}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}

/**
 * Failure counterpart to `EmptyState`. Surfaces the message the API returned and
 * offers a retry rather than stranding the user on a dead panel.
 */
export function ErrorState({
  message,
  onRetry,
  correlationId,
}: {
  message: string;
  onRetry?: () => void;
  correlationId?: string;
}) {
  // Proxy pages mention compose profiles and image tags. Those are for whoever
  // runs the server. A shop owner only needs to know the screen will load again.
  const operatorNote = /compose_profiles|not deployed on this host|_tag\b|container is running behind/i.test(message);
  const network = /(?:network|failed to fetch|http \d{3}|syntaxerror|typeerror|stack trace)/i.test(message);
  const unavailable = operatorNote || /temporarily unavailable/i.test(message);

  return (
    <div className="empty-state empty-state--error">
      <div className="empty-state__icon">
        <Icon name="alert" />
      </div>
      <h2 className="empty-state__title">{unavailable ? "Temporarily unavailable" : "That didn't load"}</h2>
      <p className="muted">
        {unavailable
          ? "This usually clears in a moment. Try again, and contact support if it stays this way."
          : network
            ? "MobiStack could not reach the service. Check the connection and try again."
            : message}
      </p>
      {network && (
        <details className="error-details">
          <summary>Technical details</summary>
          <code>{message}</code>
        </details>
      )}
      {correlationId && <p className="faint">Reference {correlationId}</p>}
      <div className="empty-state__support">
        {onRetry && (
          <button className="btn ghost" type="button" onClick={onRetry}>
            <Icon name="refresh" />
            Try again
          </button>
        )}
        <a className="btn ghost" href="/support">
          Get support
        </a>
      </div>
    </div>
  );
}
