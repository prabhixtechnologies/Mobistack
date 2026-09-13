import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SkipLink } from "./SkipLink";
import { LogoMark } from "./LogoMark";

describe("MobiStack keyboard and names", () => {
  it("exposes a skip link", () => {
    const { getByRole } = render(<SkipLink />);
    expect(getByRole("link", { name: "Skip to main content" }).getAttribute("href")).toBe("#main-content");
  });

  it("renders the product mark", () => {
    const { container } = render(<LogoMark />);
    expect(container.querySelector("svg")).toBeTruthy();
  });
});
