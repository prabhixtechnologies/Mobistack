import { describe, expect, it } from "vitest";
import { NAV_DESTINATIONS, activeTab, allowedWhileUnpaid, destinationFor, routeContext } from "./navigation";

describe("navigation", () => {
  it("keeps the primary rail to six destinations", () => {
    expect(NAV_DESTINATIONS.map((destination) => destination.label)).toEqual([
      "Home",
      "Sell",
      "Repairs",
      "Inventory",
      "Catalog",
      "Reports",
    ]);
  });

  it("files supporting records under the workflow they support", () => {
    expect(destinationFor("/customers")?.id).toBe("sell");
    expect(destinationFor("/suppliers")?.id).toBe("inventory");
    expect(destinationFor("/movements")?.id).toBe("inventory");
    expect(destinationFor("/inventory/catalog-links")?.id).toBe("inventory");
    expect(destinationFor("/compatibility/abc")?.id).toBe("catalog");
    expect(destinationFor("/devices/123")?.id).toBe("catalog");
    expect(destinationFor("/members")?.id).toBe("settings");
    expect(destinationFor("/notifications")).toBeUndefined();
  });

  it("marks the most specific tab, not the shortest prefix", () => {
    const catalog = destinationFor("/commons/standing")!;
    expect(activeTab(catalog, "/commons/standing")?.to).toBe("/commons/standing");
    expect(activeTab(catalog, "/commons/devices/42")?.to).toBe("/commons");
  });

  it("does not let Home claim every path", () => {
    expect(destinationFor("/sales")?.id).toBe("sell");
    expect(destinationFor("/")?.id).toBe("home");
  });

  it("keeps page titles and the unpaid allow-list", () => {
    expect(routeContext("/inventory")).toEqual({ title: "Inventory", section: "Inventory" });
    expect(routeContext("/support").title).toBe("Support");
    expect(allowedWhileUnpaid("/billing")).toBe(true);
    expect(allowedWhileUnpaid("/support")).toBe(true);
    expect(allowedWhileUnpaid("/sales")).toBe(false);
    expect(allowedWhileUnpaid("/commons")).toBe(false);
    expect(allowedWhileUnpaid("/commons", "?category=DISPLAY_CONNECTOR")).toBe(true);
    expect(allowedWhileUnpaid("/commons", "?brands=692f18b7-d045-4fd0-9755-d3ba68183c8f&category=DISPLAY_CONNECTOR")).toBe(
      true,
    );
  });
});
