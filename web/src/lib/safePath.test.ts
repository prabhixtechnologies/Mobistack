import { describe, expect, it } from "vitest";
import { loginPathFor, returnPathFrom, safeAppPath } from "./safePath";

describe("where a sign-in comes back to", () => {
  it("keeps a refresh on the page that was open", () => {
    expect(loginPathFor("/commons")).toBe("/login?return=%2Fcommons");
    expect(returnPathFrom("return=%2Fcommons")).toBe("/commons");
  });

  it("keeps the query that was on that page", () => {
    expect(loginPathFor("/commons", "?q=iphone")).toBe("/login?return=%2Fcommons%3Fq%3Diphone");
    expect(returnPathFrom("return=%2Fcommons%3Fq%3Diphone")).toBe("/commons?q=iphone");
  });

  it("uses the dashboard only when nothing else was open", () => {
    expect(loginPathFor("/")).toBe("/login");
    expect(returnPathFrom("")).toBe("/");
  });

  it("refuses a return path that leaves the app", () => {
    expect(safeAppPath("//evil.example")).toBe("/");
    expect(safeAppPath("https://evil.example/commons")).toBe("/");
    expect(returnPathFrom("return=%2F%2Fevil.example")).toBe("/");
    expect(returnPathFrom("return=%2Flogin")).toBe("/");
  });

  it("keeps a reason on the sign-in url without dropping the page", () => {
    expect(loginPathFor("/commons", "", { reason: "expired", sso: "0" }))
      .toBe("/login?reason=expired&sso=0&return=%2Fcommons");
  });
});
