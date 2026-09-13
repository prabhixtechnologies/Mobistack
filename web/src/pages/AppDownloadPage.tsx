import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../lib/api";
import { BRAND } from "../lib/brand";
import { BrandFooter, BrandMark } from "../ui/BrandMark";

interface PackageInfo {
  platform: string;
  available: boolean;
  filename: string;
  sizeBytes: number;
  url: string;
  contentType: string;
}

interface Catalog {
  android: PackageInfo;
  ios: PackageInfo;
}

const STORE_URL = ((import.meta.env.VITE_STORE_URL as string | undefined) || "https://store.prabhixtechnologies.com")
  .replace(/\/$/, "");

function fileHref(kind: "android" | "ios", pkg?: PackageInfo): string {
  if (kind === "ios") {
    return pkg?.url || "/download/ios";
  }
  return pkg?.url || `${STORE_URL}/mobistack/android.apk`;
}

function displayUrl(href: string): string {
  if (/^https?:\/\//i.test(href)) {
    return href;
  }
  return `${BRAND.publicOrigin.replace(/\/+$/, "")}${href}`;
}

function formatSize(bytes: number): string {
  if (!bytes) {
    return "";
  }
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AppDownloadPage() {
  const { platform } = useParams();
  const kind = platform?.toLowerCase();
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Catalog>("/api/v1/public/downloads")
      .then(setCatalog)
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <div className="app-dl-shell">
      <div className="app-dl page">
        <BrandMark />
        {kind === "ios" || kind === "android" ? (
          <PlatformCard kind={kind} pkg={kind === "ios" ? catalog?.ios : catalog?.android} error={error} />
        ) : (
          <Hub catalog={catalog} error={error} />
        )}
      </div>
      <BrandFooter />
    </div>
  );
}

function Hub({ catalog, error }: { catalog: Catalog | null; error: string | null }) {
  return (
    <>
      <h1>Get the {BRAND.product} app</h1>
      <p className="muted">
        Direct downloads from {BRAND.organization}. Android installs come from the company store.
        iOS needs a signed package from your shop.
      </p>
      {error && <div className="error">{error}</div>}
      <div className="app-dl__grid">
        <PackageCard kind="android" pkg={catalog?.android} />
        <PackageCard kind="ios" pkg={catalog?.ios} />
      </div>
    </>
  );
}

function PlatformCard({
  kind,
  pkg,
  error,
}: {
  kind: "android" | "ios";
  pkg?: PackageInfo;
  error: string | null;
}) {
  const ios = kind === "ios";
  const href = fileHref(kind, pkg);
  return (
    <>
      <p className="muted">
        <Link to="/app">All downloads</Link>
      </p>
      <h1>{ios ? "iOS" : "Android"} app</h1>
      <p className="muted">
        {ios
          ? "This link serves the signed iOS package when one is published. iPhones cannot install a raw IPA the way Android installs an APK — use your shop provisioning or TestFlight if the file is not trusted on the device."
          : "Android packages are published on the Prabhix company store. Open the download on the phone and allow installs from the browser if Android asks."}
      </p>
      {error && <div className="error">{error}</div>}
      <p className="app-dl__url">
        <code>{displayUrl(href)}</code>
      </p>
      {pkg?.available ? (
        <p className="muted">
          {pkg.filename}
          {pkg.sizeBytes ? ` · ${formatSize(pkg.sizeBytes)}` : ""}
        </p>
      ) : (
        <p className="muted">
          {ios
            ? "The iOS package is not published yet."
            : "The Android package is not on the company store yet."}
        </p>
      )}
      <p className="app-dl__actions">
        <a className="btn" href={href} download={ios ? "MobiStack.ipa" : "MobiStack.apk"}>
          Download {ios ? "iOS" : "Android"}
        </a>
        <Link className="btn ghost" to={ios ? "/app/android" : "/app/ios"}>
          {ios ? "Android instead" : "iOS instead"}
        </Link>
      </p>
    </>
  );
}

function PackageCard({ kind, pkg }: { kind: "android" | "ios"; pkg?: PackageInfo }) {
  const ios = kind === "ios";
  const href = fileHref(kind, pkg);
  return (
    <article className="card app-dl__card">
      <h2>{ios ? "iOS" : "Android"}</h2>
      <p className="muted">{ios ? "Signed IPA when published" : "Company store APK"}</p>
      <p className="app-dl__url">
        <code>{displayUrl(href)}</code>
      </p>
      {pkg?.available ? (
        <p className="muted">{pkg.filename} · {formatSize(pkg.sizeBytes)}</p>
      ) : (
        <p className="muted">{ios ? "IPA not uploaded yet" : "APK not uploaded yet"}</p>
      )}
      <p className="app-dl__actions">
        <a className="btn" href={href} download={ios ? "MobiStack.ipa" : "MobiStack.apk"}>
          Download
        </a>
        <Link className="btn ghost" to={ios ? "/app/ios" : "/app/android"}>
          Install notes
        </Link>
      </p>
    </article>
  );
}
