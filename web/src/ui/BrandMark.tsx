import { BRAND, copyrightLine } from "../lib/brand";

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand" style={{ padding: compact ? 0 : undefined }}>
      <div className="brand-mark">F</div>
      <div>
        <div className="brand-name">{BRAND.product}</div>
        {!compact && <div className="faint" style={{ fontSize: 12, marginTop: 2 }}>{BRAND.organization}</div>}
      </div>
    </div>
  );
}

export function BrandFooter() {
  return (
    <footer className="brand-footer">
      <div>{BRAND.tagline}</div>
      <div>{copyrightLine()}</div>
    </footer>
  );
}
