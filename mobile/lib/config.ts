/**
 * Build-time Identity / API configuration for the native app.
 *
 * Identity OIDC is required. Release / EAS profiles bake EXPO_PUBLIC_IDENTITY_ISSUER.
 * Local default points at the sibling Identity service on the host machine.
 */
export const IDENTITY_ISSUER = (
  process.env.EXPO_PUBLIC_IDENTITY_ISSUER ?? "http://localhost:8081"
).replace(/\/+$/, "");

/** Matches the client id seeded by Identity's RegisteredClientSeeder for this Expo scheme. */
export const OIDC_CLIENT_ID = "prabhix-mobistack-android";

/** True when a non-empty issuer is configured (always required for sign-in). */
export function isOidcConfigured(): boolean {
  return IDENTITY_ISSUER.length > 0;
}

/** @deprecated Use isOidcConfigured — Identity is the only supported login path. */
export function isOidcEnabled(): boolean {
  return isOidcConfigured();
}
