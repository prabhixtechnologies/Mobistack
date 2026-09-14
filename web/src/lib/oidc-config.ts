import { configureOidc } from "@prabhix/oidc-client";
import { IDENTITY_ISSUER } from "./config";
import { safeAppPath } from "./safePath";

configureOidc({
  issuer: IDENTITY_ISSUER,
  clientId: "prabhix-mobistack",
  safeReturnTo: safeAppPath,
  describeOauthError(code) {
    if (code === "invalid_request" || code === "invalid_client") {
      return "MobiStack is not configured correctly for sign-in. Contact support.";
    }
    return undefined;
  },
});
