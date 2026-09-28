import { describe, expect, it } from "vitest";
import { TAG_TONES, marks, toneFor } from "@prabhix/brand";

/**
 * That the linked brand package can be imported at all.
 *
 * `@prabhix/brand` is a `file:` dependency on a sibling checkout, which makes its node_modules
 * entry a link out of this repository, and Vite checks `server.fs.allow` against the real path.
 * Get that wrong and every import of the package fails with "Denied ID", not a missing file — a
 * whole-suite error with nothing in it naming the config line responsible. That is what happened
 * to Mailroom's accessibility tests: they could not run for weeks and the CI failure pointed at
 * the test file.
 *
 * Importing `marks` is the part that matters. It is the only export that reaches for an asset,
 * through `new URL("../marks/...", import.meta.url)`, and so the only one the check can refuse.
 *
 * The assertion accepts a data URL as well as a path because the marks are small enough for Vite
 * to inline, and which of the two comes back is a build detail — `assetsInlineLimit`, and the
 * size of an SVG somebody may well edit. What is being tested is that a URL comes back at all.
 */
describe("the linked brand package", () => {
  it("resolves its mark assets", () => {
    expect(marks.favicon).toMatch(/^data:image\/svg\+xml,|favicon\.svg$/);
  });

  it("exports the generated tag swatches", () => {
    expect(TAG_TONES).toContain("ochre");
    expect(TAG_TONES).toContain(toneFor("any seed at all"));
  });
});
