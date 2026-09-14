export const IDENTITY_ISSUER = (import.meta.env.VITE_IDENTITY_ISSUER ?? "").replace(/\/+$/, "");

/** Identity's hosted account page: password, passkeys, sessions, connected accounts. */
export const ACCOUNT_URL = IDENTITY_ISSUER
  ? `${IDENTITY_ISSUER}/account?return_to=${encodeURIComponent(window.location.origin)}`
  : "";
