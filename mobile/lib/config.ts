/**
 * Build-time Identity / API configuration for the native app.
 *
 * <p>{@code EXPO_PUBLIC_IDENTITY_ISSUER} turns on hosted login. Leave it blank for local
 * development against a MobiStack backend that still accepts password forms.
 */
export const IDENTITY_ISSUER = (process.env.EXPO_PUBLIC_IDENTITY_ISSUER ?? "").replace(/\/+$/, "");

/** Matches the client id seeded by Identity's RegisteredClientSeeder for this Expo scheme. */
export const OIDC_CLIENT_ID = "prabhix-mobistack-android";

export function isOidcEnabled(): boolean {
  return IDENTITY_ISSUER.length > 0;
}
