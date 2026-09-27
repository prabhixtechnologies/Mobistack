import { describe, expect, it } from "vitest";
import { afterAuthPath } from "./plan";
import { routePermission } from "./permissions";

describe("returning owner routing", () => {
  it("sends a paid full shop to the dashboard", () => {
    expect(afterAuthPath({ paymentRequired: false, catalogOnly: false, features: ["DASHBOARD", "SALES"] })).toBe("/");
  });

  it("keeps catalog-only plans on the shared catalog", () => {
    expect(afterAuthPath({ paymentRequired: false, catalogOnly: true, features: ["COMPATIBILITY"] })).toBe("/commons");
  });
});

describe("permission routes", () => {
  it("requires support capability for the support desk", () => {
    expect(routePermission("/support")).toBe("SUPPORT_READ");
  });
});
