import { useParams } from "react-router-dom";
import { BRAND } from "../lib/brand";
import { BrandMark } from "../ui/BrandMark";

export function AppDownloadPage() {
  const { platform } = useParams();
  const ios = platform?.toLowerCase() === "ios";
  return (
    <div className="page" style={{ maxWidth: 560, margin: "48px auto" }}>
      <BrandMark />
      <h1>{ios ? "iOS build" : "Android build"}</h1>
      <p className="muted">
        Install the latest MobiStack counter app from {BRAND.organization}. JavaScript fixes also arrive
        over the air on the production channel. When this page is used as a force-update target, the
        native binary is newer than the one on the device.
      </p>
      <p>
        Place the signed {ios ? "IPA" : "APK"} at this path on the server, or link it from EAS /
        TestFlight / Play. Contact {BRAND.supportEmail} if you need a build.
      </p>
    </div>
  );
}
