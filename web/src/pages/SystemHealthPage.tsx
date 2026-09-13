import { useEffect, useState } from "react";
import { useResource } from "../lib/useResource";
import type { SystemStatus } from "../lib/types";
import { PageHeader } from "../ui/PageHeader";
import { ErrorState } from "../ui/EmptyState";
import { Icon } from "../ui/navIcons";

const REFRESH_MS = 15_000;

/** Micrometer reports an absent meter as -1; render those as unavailable, not zero. */
function known(value: number): boolean {
  return value >= 0;
}

function formatDuration(seconds: number): string {
  if (!known(seconds)) {
    return "—";
  }
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  if (days > 0) {
    return `${days}d ${hours}h`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m`;
}

function formatBytes(bytes: number): string {
  if (!known(bytes) || bytes === 0) {
    return "—";
  }
  const mb = bytes / 1_048_576;
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`;
}

function statusTone(status: string): "GREEN" | "ORANGE" | "RED" {
  if (status === "UP") {
    return "GREEN";
  }
  return status === "DOWN" || status === "OUT_OF_SERVICE" ? "RED" : "ORANGE";
}

/** Friendly names for the component keys actuator reports. */
const COMPONENT_LABELS: Record<string, string> = {
  db: "PostgreSQL",
  redis: "Redis",
  diskSpace: "Disk space",
  mail: "Mail server",
  ping: "Application",
  livenessState: "Liveness probe",
  readinessState: "Readiness probe",
  ssl: "TLS certificates",
};

/**
 * Operational view of the running backend: dependency health plus the runtime
 * numbers that explain a slowdown.
 *
 * Polls rather than streams — the backend has no WebSocket channel, and at a
 * 15-second cadence a single aggregated request is cheaper than standing up one.
 */
export function SystemHealthPage() {
  const health = useResource<SystemStatus>("/api/v1/system/health");
  const [live, setLive] = useState(true);

  useEffect(() => {
    if (!live) {
      return;
    }
    const tick = () => {
      if (typeof document !== "undefined" && document.hidden) return;
      health.reload();
    };
    const timer = window.setInterval(tick, REFRESH_MS);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [live, health.reload]);

  const runtime = health.data?.runtime;
  const heapPercent =
    runtime && known(runtime.heapUsedBytes) && runtime.heapMaxBytes > 0
      ? Math.round((runtime.heapUsedBytes / runtime.heapMaxBytes) * 100)
      : null;
  const poolPercent =
    runtime && known(runtime.dbPoolActive) && runtime.dbPoolMax > 0
      ? Math.round((runtime.dbPoolActive / runtime.dbPoolMax) * 100)
      : null;
  const errorRate =
    runtime && runtime.requestCount > 0
      ? (runtime.serverErrorCount / runtime.requestCount) * 100
      : 0;

  return (
    <div className="page">
      <PageHeader
        kicker="Operations"
        title="System health"
        subtitle="Live status of the services MobiStack depends on, and how hard this instance is working."
        actions={
          <div className="row">
            <button
              className={`btn ${live ? "soft" : "ghost"} btn--sm`}
              type="button"
              onClick={() => setLive((value) => !value)}
              aria-pressed={live}
            >
              <span className={`live-dot${live ? " live-dot--on" : ""}`} aria-hidden />
              {live ? "Live" : "Paused"}
            </button>
            <button className="btn ghost btn--sm" type="button" onClick={health.reload}>
              <Icon name="refresh" />
              Refresh
            </button>
          </div>
        }
      />

      {health.error ? (
        <ErrorState message={health.error} onRetry={health.reload} />
      ) : (
        <>
          <section className={`card status-hero status-hero--${statusTone(health.data?.status ?? "UNKNOWN")}`}>
            <div className="status-hero__icon">
              <Icon name={health.data?.status === "UP" ? "check" : "alert"} />
            </div>
            <div>
              <h2>
                {health.data?.status === "UP"
                  ? "All systems operational"
                  : health.data
                    ? `Degraded — reporting ${health.data.status}`
                    : "Checking…"}
              </h2>
              <p className="muted">
                {health.data
                  ? `${health.data.components.filter((component) => component.status === "UP").length} of ${health.data.components.length} components healthy · up ${formatDuration(runtime?.uptimeSeconds ?? -1)}`
                  : "Reading status from the backend."}
              </p>
            </div>
          </section>

          <div className="grid-4">
            <Metric
              label="Requests served"
              value={runtime ? runtime.requestCount.toLocaleString("en-IN") : "—"}
              tint="blue"
            />
            <Metric
              label="Server errors"
              value={runtime ? `${runtime.serverErrorCount} (${errorRate.toFixed(2)}%)` : "—"}
              tint={errorRate > 1 ? "rose" : "green"}
            />
            <Metric
              label="Mean response"
              value={runtime ? `${runtime.meanRequestMillis.toFixed(0)} ms` : "—"}
              tint="amber"
            />
            <Metric
              label="CPU load"
              value={runtime && known(runtime.cpuUsage) ? `${(runtime.cpuUsage * 100).toFixed(1)}%` : "—"}
              tint="violet"
            />
          </div>

          <div className="grid-2">
            <section className="card">
              <h2 className="section-title">Dependencies</h2>
              {health.loading && !health.data ? (
                <div className="stack">
                  <div className="skeleton" />
                  <div className="skeleton" />
                  <div className="skeleton" />
                </div>
              ) : (
                <ul className="component-list">
                  {health.data?.components.map((component) => (
                    <li key={component.name} className="component">
                      <span className={`status-dot status-dot--${statusTone(component.status)}`} aria-hidden />
                      <div className="component__detail">
                        <strong>{COMPONENT_LABELS[component.name] ?? component.name}</strong>
                        {component.detail && <span className="faint">{component.detail}</span>}
                      </div>
                      <span className={`badge ${statusTone(component.status)}`}>{component.status}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card">
              <h2 className="section-title">Resources</h2>
              <div className="stack">
                <Meter
                  label="Java heap"
                  percent={heapPercent}
                  caption={
                    runtime
                      ? `${formatBytes(runtime.heapUsedBytes)} of ${formatBytes(runtime.heapMaxBytes)}`
                      : "—"
                  }
                />
                <Meter
                  label="Database connections"
                  percent={poolPercent}
                  caption={
                    runtime && known(runtime.dbPoolActive)
                      ? `${runtime.dbPoolActive} active of ${runtime.dbPoolMax}`
                      : "Pool metrics unavailable"
                  }
                />
                <dl className="detail-list">
                  <div>
                    <dt>Uptime</dt>
                    <dd>{formatDuration(runtime?.uptimeSeconds ?? -1)}</dd>
                  </div>
                </dl>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
}

function Metric({ label, value, tint }: { label: string; value: string; tint: string }) {
  return (
    <div className={`card metric-card tint-${tint}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}

function Meter({ label, percent, caption }: { label: string; percent: number | null; caption: string }) {
  const tone = percent === null ? "neutral" : percent > 90 ? "RED" : percent > 75 ? "ORANGE" : "GREEN";
  return (
    <div className="meter">
      <div className="spread">
        <span className="metric-label">{label}</span>
        <span className="faint">{percent === null ? "—" : `${percent}%`}</span>
      </div>
      <div
        className="meter__track"
        role="progressbar"
        aria-label={label}
        aria-valuenow={percent ?? undefined}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div className={`meter__fill meter__fill--${tone}`} style={{ width: `${percent ?? 0}%` }} />
      </div>
      <span className="faint">{caption}</span>
    </div>
  );
}
