import { commonsBrowsePath, legacyCommonsTarget } from "./commonsQuery";

const BRAND = "692f18b7-d045-4fd0-9755-d3ba68183c8f";

describe("commonsBrowsePath", () => {
  it("builds the combined brand and part-type address", () => {
    expect(commonsBrowsePath({ brands: BRAND, category: "DISPLAY_CONNECTOR" })).toBe(
      `/commons?brands=${BRAND}&category=DISPLAY_CONNECTOR`,
    );
  });

  it("keeps a part type or a brand on their own", () => {
    expect(commonsBrowsePath({ category: "DISPLAY_CONNECTOR" })).toBe("/commons?category=DISPLAY_CONNECTOR");
    expect(commonsBrowsePath({ brands: BRAND })).toBe(`/commons?brands=${BRAND}`);
    expect(commonsBrowsePath()).toBe("/commons");
    expect(commonsBrowsePath({ brands: "  ", category: "" })).toBe("/commons");
  });
});

describe("legacyCommonsTarget", () => {
  it("sends the old brand path to the query-param catalog", () => {
    expect(legacyCommonsTarget(`/commons/brands/${BRAND}`, "?category=DISPLAY_CONNECTOR")).toBe(
      `/commons?brands=${BRAND}&category=DISPLAY_CONNECTOR`,
    );
  });

  it("sends the old part-type path to the query-param catalog", () => {
    expect(legacyCommonsTarget("/commons/categories/DISPLAY_CONNECTOR")).toBe(
      "/commons?category=DISPLAY_CONNECTOR",
    );
  });
});
