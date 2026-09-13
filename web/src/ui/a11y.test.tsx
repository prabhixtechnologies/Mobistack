import { render } from "@testing-library/react";
import axe from "axe-core";
import { describe, expect, it } from "vitest";
import { EmptyState, ErrorState } from "./EmptyState";
import { LogoMark } from "./LogoMark";
import { Modal } from "./Modal";
import { PageHeader } from "./PageHeader";
import { SkipLink } from "./SkipLink";

const RULES = ["button-name", "link-name", "image-alt", "label"];

async function violations(container: HTMLElement) {
  const results = await axe.run(container, {
    runOnly: { type: "rule", values: RULES },
  });
  return results.violations.map((v) => ({ rule: v.id, html: v.nodes.map((n) => n.html) }));
}

describe("MobiStack keyboard and names", () => {
  it("exposes a skip link", () => {
    const { getByRole } = render(<SkipLink />);
    expect(getByRole("link", { name: "Skip to main content" }).getAttribute("href")).toBe("#main-content");
  });

  it("renders the product mark", () => {
    const { container } = render(<LogoMark />);
    expect(container.querySelector("svg")).toBeTruthy();
  });

  it("names page headers and empty states", async () => {
    const { container, getByRole } = render(
      <main>
        <PageHeader title="Members" subtitle="Invite people who work this shop." />
        <EmptyState title="No members yet" hint="Send an invite to add the first person." action={<button type="button">Invite</button>} />
      </main>,
    );
    expect(getByRole("heading", { name: "Members" })).toBeTruthy();
    expect(getByRole("button", { name: "Invite" })).toBeTruthy();
    expect(await violations(container)).toEqual([]);
  });

  it("names retry on an error state", async () => {
    const { container, getByRole } = render(<ErrorState message="The shop list failed." onRetry={() => undefined} />);
    expect(getByRole("button", { name: "Try again" })).toBeTruthy();
    expect(await violations(container)).toEqual([]);
  });

  it("names the close control on a dialog", async () => {
    const { container, getByRole } = render(
      <Modal open title="Receive stock" onClose={() => undefined}>
        <p>Count the units, then save.</p>
      </Modal>,
    );
    expect(getByRole("dialog", { name: "Receive stock" })).toBeTruthy();
    expect(getByRole("button", { name: "Close dialog" })).toBeTruthy();
    expect(await violations(container)).toEqual([]);
  });
});
