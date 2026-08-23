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
      <strong>{title}</strong>
      {hint && <p className="muted">{hint}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}

/**
 * Failure counterpart to `EmptyState`. Surfaces the message the API returned and
 * offers a retry rather than stranding the user on a dead panel.
 */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty-state empty-state--error">
      <div className="empty-state__icon">
        <Icon name="alert" />
      </div>
      <strong>That didn't load</strong>
      <p className="muted">{message}</p>
      {onRetry && (
        <div className="empty-state__action">
          <button className="btn ghost" type="button" onClick={onRetry}>
            <Icon name="refresh" />
            Try again
          </button>
        </div>
      )}
    </div>
  );
}
