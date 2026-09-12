import { Link } from "react-router-dom";
import { BRAND } from "../lib/brand";
import { LogoMark } from "./LogoMark";

export function BrandMark({
  compact = false,
  inverse = false,
}: {
  compact?: boolean;
  inverse?: boolean;
}) {
  return (
    <div className={`brand${inverse ? " brand--inverse" : ""}`} style={{ padding: compact ? 0 : undefined }}>
      <span className="brand-mark">
        <LogoMark size={34} />
      </span>
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
      <p className="brand-footer__copy">© {BRAND.copyrightYear} {BRAND.product}</p>
      <p className="brand-footer__credit">
        Built by <span className="brand-footer__author">{BRAND.organization}</span>
        {APP_VERSION ? <span className="brand-footer__ver"> · v{APP_VERSION}</span> : null}
      </p>
      <div className="brand-footer__end">
        <nav className="brand-footer__links" aria-label="Legal and support">
          <Link to="/app">Get the app</Link>
          <a href="http://store.prabhixtechnologies.com:8090/mobistack/">Android</a>
          <a href="/download/ios">iOS</a>
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/refunds">Refunds</Link>
          <a href={`mailto:${BRAND.supportEmail}`}>Support</a>
        </nav>
        <span className="brand-footer__logo">
          <span className="brand-mark brand-mark--xs">
            <LogoMark size={22} />
          </span>
          {BRAND.product}
        </span>
      </div>
    </footer>
  );
}
