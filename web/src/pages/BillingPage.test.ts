import { describe, expect, it } from "vitest";
import { featureLabel } from "./BillingPage";

describe("customer plan presentation", () => {
  it("uses customer language instead of backend feature codes", () => {
    expect(featureLabel("COMPATIBILITY")).toBe("Shared phone and part compatibility");
    expect(featureLabel("INVENTORY")).toBe("Inventory and stock control");
    expect(featureLabel("FULL_SHOP_EXTRA")).toBe("full shop extra");
  });
});
