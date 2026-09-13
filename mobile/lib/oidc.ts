import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import * as SecureStore from "expo-secure-store";
import { IDENTITY_ISSUER, OIDC_CLIENT_ID, isOidcEnabled } from "./config";

WebBrowser.maybeCompleteAuthSession();

const ID_TOKEN_KEY = "mobistack.id_token";

/**
 * Redirect registered on Identity as both {@code mobistack:/oauth2redirect} and
 * {@code mobistack://oauth2redirect}. Expo's makeRedirectUri produces the latter on device.
 */
export function redirectUri(): string {
  return AuthSession.makeRedirectUri({
    scheme: "mobistack",
    path: "oauth2redirect",
  });
}

export function discovery(): AuthSession.DiscoveryDocument {
  return {
    authorizationEndpoint: `${IDENTITY_ISSUER}/oauth2/authorize`,
    tokenEndpoint: `${IDENTITY_ISSUER}/oauth2/token`,
    endSessionEndpoint: `${IDENTITY_ISSUER}/connect/logout`,
  };
}

export interface OidcTokens {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  expiresIn: number;
}

export interface BeginLoginOptions {
  /** Pass {@code create} for Identity hosted signup (same as web {@code prompt=create}). */
  prompt?: "create" | "login" | "select_account";
}

/**
 * Opens Identity in a system browser / Custom Tab, then exchanges the code for tokens.
 *
 * <p>PKCE is mandatory server-side. No client secret — this is a public client.
 */
export async function beginLogin(options: BeginLoginOptions = {}): Promise<OidcTokens> {
  if (!isOidcEnabled()) {
    throw new Error("Identity is not configured for this build.");
  }

  const request = new AuthSession.AuthRequest({
    clientId: OIDC_CLIENT_ID,
    redirectUri: redirectUri(),
    scopes: ["openid", "profile", "email"],
    usePKCE: true,
    responseType: AuthSession.ResponseType.Code,
    prompt: options.prompt as AuthSession.Prompt | undefined,
  });

  await request.makeAuthUrlAsync(discovery());
  const result = await request.promptAsync(discovery(), { showInRecents: true });

  if (result.type === "dismiss" || result.type === "cancel") {
    throw new Error("Sign-in was cancelled.");
  }
  if (result.type !== "success" || !result.params.code) {
    const description = result.type === "error"
      ? (result.params.error_description ?? result.params.error ?? "Sign-in did not complete.")
      : "Sign-in did not complete.";
    throw new Error(description);
  }

  if (!request.codeVerifier) {
    throw new Error("Sign-in could not prove this app started it. Try again.");
  }

  return exchangeAuthorizationCode(result.params.code, request.codeVerifier);
}

async function exchangeAuthorizationCode(code: string, codeVerifier: string): Promise<OidcTokens> {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(),
    client_id: OIDC_CLIENT_ID,
    code_verifier: codeVerifier,
  });

  const response = await fetch(`${IDENTITY_ISSUER}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!response.ok) {
    throw new Error("Could not complete sign-in. Try again.");
  }

  return parseTokenResponse(await response.json());
}

/** Rotates an Identity refresh token. */
export async function refreshOidcTokens(refreshToken: string): Promise<OidcTokens> {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
    client_id: OIDC_CLIENT_ID,
  });

  const response = await fetch(`${IDENTITY_ISSUER}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!response.ok) {
    throw new Error("Session refresh failed");
  }

  return parseTokenResponse(await response.json(), refreshToken);
}

function parseTokenResponse(
  payload: {
    access_token?: string;
    refresh_token?: string;
    id_token?: string;
    expires_in?: number;
  },
  previousRefresh?: string,
): OidcTokens {
  if (!payload.access_token) {
    throw new Error("Identity returned no access token");
  }
  const refresh = payload.refresh_token ?? previousRefresh;
  if (!refresh) {
    throw new Error("Identity returned no refresh token");
  }
  return {
    accessToken: payload.access_token,
    refreshToken: refresh,
    idToken: payload.id_token,
    expiresIn: payload.expires_in ?? 1800,
  };
}

export async function rememberIdToken(idToken?: string): Promise<void> {
  if (idToken) {
    await SecureStore.setItemAsync(ID_TOKEN_KEY, idToken);
  }
}

export async function beginLogout(): Promise<void> {
  const idToken = await SecureStore.getItemAsync(ID_TOKEN_KEY);
  await SecureStore.deleteItemAsync(ID_TOKEN_KEY);

  const params = new URLSearchParams({ client_id: OIDC_CLIENT_ID });
  if (idToken) {
    params.set("id_token_hint", idToken);
  }
  const url = `${IDENTITY_ISSUER}/connect/logout?${params.toString()}`;
  await WebBrowser.openAuthSessionAsync(url, redirectUri());
}

export { isOidcEnabled };
