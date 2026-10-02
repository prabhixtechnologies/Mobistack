import { cleanup, fireEvent, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { GlobalSearch } from "./GlobalSearch";

vi.mock("../lib/auth", () => ({
  useAuth: () => ({ user: { features: ["COMPATIBILITY"], permissions: [] } }),
}));

vi.mock("../lib/access", () => ({
  useAccess: () => ({
    has: () => true,
    canOpen: () => true,
  }),
}));

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
});

afterEach(cleanup);

describe("application shell", () => {
  it("opens and closes the command palette from keyboard controls", () => {
    const { getByRole, queryByRole } = render(
      <MemoryRouter>
        <GlobalSearch />
      </MemoryRouter>,
    );

    fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    expect(getByRole("dialog", { name: "Search" })).toBeTruthy();

    fireEvent.keyDown(getByRole("combobox"), { key: "Escape" });
    expect(queryByRole("dialog", { name: "Search" })).toBeNull();
  });

  it("does not advertise full-shop actions on the Compatibility plan", () => {
    const { getByRole, getByText, queryByText } = render(
      <MemoryRouter>
        <GlobalSearch />
      </MemoryRouter>,
    );

    fireEvent.click(getByRole("button", { name: "Search phones and fitment…" }));
    expect(getByText("Look up a phone")).toBeTruthy();
    expect(queryByText("New sale")).toBeNull();
    expect(queryByText("Book a repair")).toBeNull();
    expect(queryByText("Open inventory")).toBeNull();
    expect(queryByText("Find a customer")).toBeNull();
  });
});
