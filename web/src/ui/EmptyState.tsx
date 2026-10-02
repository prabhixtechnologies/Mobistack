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
  const technical = /(?:network|failed to fetch|http \d{3}|syntaxerror|typeerror|stack trace)/i.test(message);

  return (
    <div className="empty-state empty-state--error">
      <div className="empty-state__icon">
        <Icon name="alert" />
      </div>
      <h2 className="empty-state__title">That didn't load</h2>
      <p className="muted">
        {technical ? "MobiStack could not reach the service. Check the connection and try again." : message}
      </p>
      {technical && (
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
