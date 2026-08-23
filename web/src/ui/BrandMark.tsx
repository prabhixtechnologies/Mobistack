import { Link } from "react-router-dom";
import { BRAND, copyrightLine } from "../lib/brand";

export function BrandMark({
  compact = false,
  inverse = false,
}: {
  compact?: boolean;
  inverse?: boolean;
}) {
  return (
    <div className={`brand${inverse ? " brand--inverse" : ""}`} style={{ padding: compact ? 0 : undefined }}>
      <div className="brand-mark">M</div>
      <div>
        <div className="brand-name">{BRAND.product}</div>
        {!compact && <div className="faint" style={{ fontSize: 12, marginTop: 2 }}>{BRAND.organization}</div>}
      </div>
    </div>
  );
}

const APP_VERSION = import.meta.env.VITE_APP_VERSION as string | undefined;

/**
 * Footer for the authenticated shell and standalone pages.
 *
 * Carries the legal links every page is expected to reach and, when the build
 * injects `VITE_APP_VERSION`, the running version — which turns "it's broken on
 * mine" into a reproducible report.
 */
export function BrandFooter() {
  return (
    <footer className="brand-footer">
      <div className="brand-footer__row">
        <span>{BRAND.tagline}</span>
        <nav className="brand-footer__links" aria-label="Legal and support">
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/refunds">Refunds</Link>
          <a href={`mailto:${BRAND.supportEmail}`}>{BRAND.supportEmail}</a>
        </nav>
      </div>
      <div className="brand-footer__row brand-footer__meta">
        <span>{copyrightLine()}</span>
        {APP_VERSION && <span className="faint">v{APP_VERSION}</span>}
      </div>
    </footer>
  );
}
