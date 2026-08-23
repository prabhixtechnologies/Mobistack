import { Component, type ErrorInfo, type ReactNode } from "react";
import { Icon } from "./navIcons";

interface State {
  error: Error | null;
}

/**
 * Stops a render-time throw from blanking the whole console.
 *
 * Mounted inside the app shell so the header and sidebar survive a page crash and
 * the user can navigate away instead of reaching for the browser reload button.
 * `resetKey` clears the captured error — the shell passes the pathname, so
 * navigating away recovers automatically.
 */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("Unhandled UI error", error, info.componentStack);
  }

  componentDidUpdate(previous: { resetKey?: string }): void {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }

    return (
      <div className="page">
        <div className="empty-state empty-state--error">
          <div className="empty-state__icon">
            <Icon name="alert" />
          </div>
          <strong>This screen hit an error</strong>
          <p className="muted">
            The rest of MobiStack is still running — pick another section from the sidebar, or reload to try
            this one again.
          </p>
          <p className="faint">{error.message}</p>
          <div className="empty-state__action row">
            <button className="btn" type="button" onClick={() => this.setState({ error: null })}>
              <Icon name="refresh" />
              Retry
            </button>
            <button className="btn ghost" type="button" onClick={() => window.location.reload()}>
              Reload the app
            </button>
          </div>
        </div>
      </div>
    );
  }
}
