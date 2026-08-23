import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../lib/api";
import { BRAND } from "../lib/brand";
import { BrandMark } from "../ui/BrandMark";

interface AppRelease {
  platform: string;
  minNativeBuild: number;
  latestNativeBuild: number;
  forceNativeUpdate: boolean;
  updateRequired: boolean;
  otaChannel?: string;
  storeUrl?: string;
  notes?: string;
  publicOrigin?: string;
}

export function AppDownloadPage() {
  const { platform } = useParams();
  const code = platform?.toLowerCase() === "ios" ? "IOS" : "ANDROID";
  const ios = code === "IOS";
  const [release, setRelease] = useState<AppRelease | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<AppRelease>(`/api/v1/public/app-release?platform=${code}`)
      .then(setRelease)
      .catch((err: Error) => setError(err.message));
  }, [code]);

  const storeUrl = release?.storeUrl?.trim();
  const notes = release?.notes?.trim();

  return (
    <div className="page" style={{ maxWidth: 560, margin: "48px auto" }}>
      <BrandMark />
      <h1>{ios ? "iOS build" : "Android build"}</h1>
      <p className="muted">
        Install the latest {BRAND.product} counter app from {BRAND.organization}. JavaScript fixes also
        arrive over the air on the production channel.
      </p>
      {error && <div className="error">{error}</div>}
      {release && (
        <p className="muted">
          Latest native build {release.latestNativeBuild}
          {release.minNativeBuild > 0 ? ` · minimum ${release.minNativeBuild}` : ""}
          {release.otaChannel ? ` · OTA ${release.otaChannel}` : ""}
        </p>
      )}
      {notes && <p>{notes}</p>}
      {storeUrl ? (
        <p>
          <a className="btn" href={storeUrl}>
            Download {ios ? "iOS" : "Android"}
          </a>
        </p>
      ) : (
        <p>
          A store link is not published yet. Contact {BRAND.supportEmail} if you need a build.
        </p>
      )}
    </div>
  );
}
