import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@prabhixtechnologies/oidc-client", () => ({
  isOidcEnabled: () => true,
  configureOidc: () => undefined,
}));

import { renewSession } from "./api";

function reply(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status });
}

async function settle<T>(promise: Promise<T>): Promise<T> {
  await vi.runAllTimersAsync();
  return promise;
}

describe("renewing a 30-day sign-in", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("survives a server hiccup and a dropped network, then renews", async () => {
    vi.useFakeTimers();
    const fetch = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(reply(503))
      .mockResolvedValueOnce(reply(200, { accessToken: "fresh" }));
    vi.stubGlobal("fetch", fetch);

    expect(await settle(renewSession())).toBe("ok");
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it("signs out only when the server refuses the credential", async () => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockResolvedValue(reply(401, { code: "UNAUTHENTICATED" }));
    vi.stubGlobal("fetch", fetch);

    expect(await settle(renewSession())).toBe("signed-out");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("keeps the sign-in when the network never comes back", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    expect(await settle(renewSession())).toBe("unavailable");
  });
});
