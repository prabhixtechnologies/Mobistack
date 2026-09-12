/**
 * Build-time Identity / API configuration for the native app.
 *
 * {@code EXPO_PUBLIC_IDENTITY_ISSUER} turns on hosted login (required for normal builds).
 * Leave blank only for a deliberate legacy-password lab against a backend that still accepts it.
 */
export const IDENTITY_ISSUER = (
  process.env.EXPO_PUBLIC_IDENTITY_ISSUER ?? "http://localhost:8081"
).replace(/\/+$/, "");

/** Matches the client id seeded by Identity's RegisteredClientSeeder for this Expo scheme. */
export const OIDC_CLIENT_ID = "prabhix-mobistack-android";

export function isOidcEnabled(): boolean {
  return IDENTITY_ISSUER.length > 0;
}
