import { afterEach, describe, expect, it, vi } from "vitest";

const beginStepUp = vi.hoisted(() => vi.fn(() => Promise.resolve()));

vi.mock("@prabhixtechnologies/oidc-client", () => ({
  isOidcEnabled: () => true,
  configureOidc: () => undefined,
  beginStepUp,
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
    beginStepUp.mockClear();
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
    expect(beginStepUp).not.toHaveBeenCalled();
  });

  it("asks for a fresh proof when staff change network, and keeps the sign-in", async () => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockResolvedValue(reply(403, { code: "STEP_UP_REQUIRED" }));
    vi.stubGlobal("fetch", fetch);

    expect(await settle(renewSession())).toBe("step-up");
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(beginStepUp).toHaveBeenCalledTimes(1);
  });

  it("keeps the sign-in when the network never comes back", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    expect(await settle(renewSession())).toBe("unavailable");
  });
});
