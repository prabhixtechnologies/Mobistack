import { fireEvent, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeAll, describe, expect, it } from "vitest";
import { GlobalSearch } from "./GlobalSearch";

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
});
