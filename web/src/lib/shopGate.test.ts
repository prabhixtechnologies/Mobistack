import { describe, expect, it } from "vitest";
import { afterAuthPath, allowedOnPlan } from "./plan";
import { routePermission } from "./permissions";

describe("returning owner routing", () => {
  it("sends a paid full shop to the dashboard", () => {
    expect(afterAuthPath({ paymentRequired: false, catalogOnly: false, features: ["DASHBOARD", "SALES"] })).toBe("/");
  });

  it("opens home for a compatibility plan, with the sales floor still out of reach", () => {
    const user = { paymentRequired: false, catalogOnly: true, features: ["COMPATIBILITY"] };
    expect(afterAuthPath(user)).toBe("/");
    expect(allowedOnPlan("/", user.features)).toBe(true);
    expect(allowedOnPlan("/sales", user.features)).toBe(false);
    expect(allowedOnPlan("/inventory", user.features)).toBe(false);
  });
});

describe("permission routes", () => {
  it("requires support capability for the support desk", () => {
    expect(routePermission("/support")).toBe("SUPPORT_READ");
  });
});
