import { IDENTITY_ISSUER } from "./config";

/**
 * The authorization code flow with PKCE, against Prabhix Identity.
 *
 * <p>When Identity is configured, MobiStack redirects to the hosted login page rather than
 * collecting a password here. The session cookie set on Identity's origin is what lets somebody
 * who signed in to OneOps open MobiStack without being asked again.
 *
 * <p>PKCE rather than a client secret because a browser app cannot keep a secret — anything shipped
 * to the browser is readable by whoever receives it.
 */

/** Matches the client id seeded by Identity's RegisteredClientSeeder. */
const CLIENT_ID = "prabhix-mobistack";

const VERIFIER_KEY = "pbx_pkce_verifier";
const STATE_KEY = "pbx_oauth_state";
const RETURN_KEY = "pbx_oauth_return_to";
const ID_TOKEN_KEY = "pbx_id_token";

export function isOidcEnabled(): boolean {
  return IDENTITY_ISSUER.length > 0;
}

export function redirectUri(): string {
  return `${window.location.origin}/auth/callback`;
}

/**
 * Sends the browser to the hosted login page.
 *
 * @param returnTo where to land afterwards, remembered locally rather than round-tripped through the
 *     provider.
 */
export async function beginLogin(returnTo?: string): Promise<void> {
  const verifier = randomUrlSafe(64);
  const state = randomUrlSafe(32);

  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);
  if (returnTo) sessionStorage.setItem(RETURN_KEY, returnTo);

  const params = new URLSearchParams({
    response_type: "code",
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    scope: "openid profile email",
    state,
    code_challenge: await sha256Base64Url(verifier),
    code_challenge_method: "S256",
  });

  window.location.assign(`${IDENTITY_ISSUER}/oauth2/authorize?${params.toString()}`);
}

export interface OidcTokens {
  accessToken: string;
  expiresIn: number;
  idToken?: string;
}

/**
 * Redeems the code the provider sent back.
 *
 * @throws if `state` does not match what this tab stored.
 */
export async function completeLogin(search: URLSearchParams): Promise<{
  tokens: OidcTokens;
  returnTo: string;
}> {
  const error = search.get("error");
  if (error) {
    throw new Error(search.get("error_description") ?? describeOauthError(error));
  }

  const code = search.get("code");
  const state = search.get("state");
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  const returnTo = sessionStorage.getItem(RETURN_KEY) ?? "/";

  sessionStorage.removeItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(RETURN_KEY);

  if (!code || !state || !verifier) {
    throw new Error("That sign-in link is incomplete. Start again from the sign-in page.");
  }
  if (!expectedState || state !== expectedState) {
    throw new Error("That sign-in response did not match this browser. Start again.");
  }

  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(),
    client_id: CLIENT_ID,
    code_verifier: verifier,
  });

  const response = await fetch(`${IDENTITY_ISSUER}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    credentials: "include",
    body,
  });

  if (!response.ok) {
    throw new Error("Could not complete sign-in. Start again from the sign-in page.");
  }

  const payload = (await response.json()) as {
    access_token: string;
    expires_in: number;
    id_token?: string;
  };

  return {
    tokens: {
      accessToken: payload.access_token,
      expiresIn: payload.expires_in,
      idToken: payload.id_token,
    },
    returnTo,
  };
}

/** Remembers the id token from a completed sign-in, so sign-out has something to present. */
export function rememberIdToken(idToken?: string): void {
  if (idToken) sessionStorage.setItem(ID_TOKEN_KEY, idToken);
}

/**
 * Ends the session at the provider, not only here.
 */
export function beginLogout(): void {
  const idToken = sessionStorage.getItem(ID_TOKEN_KEY);
  sessionStorage.removeItem(ID_TOKEN_KEY);

  const params = new URLSearchParams({ client_id: CLIENT_ID });
  if (idToken) {
    params.set("id_token_hint", idToken);
    params.set("post_logout_redirect_uri", `${window.location.origin}/`);
  }
  window.location.assign(`${IDENTITY_ISSUER}/connect/logout?${params.toString()}`);
}

function describeOauthError(code: string): string {
  switch (code) {
    case "access_denied":
      return "Sign-in was cancelled.";
    case "invalid_request":
    case "invalid_client":
      return "MobiStack is not configured correctly for sign-in. Contact support.";
    default:
      return "Sign-in did not complete. Start again from the sign-in page.";
  }
}

function randomUrlSafe(bytes: number): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return base64UrlEncode(buffer);
}

async function sha256Base64Url(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
