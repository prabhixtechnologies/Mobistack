import type { GroupDevice } from "./types";
import type { ReactNode } from "react";
import { createElement, Fragment } from "react";

export function deviceLabel(device: Pick<GroupDevice, "brandName" | "deviceName" | "variant">): string {
  return [device.brandName, device.deviceName, device.variant].filter(Boolean).join(" ");
}

export function phoneLabel(device: {
  brandName?: string;
  name: string;
  variant?: string;
}): string {
  return [device.brandName, device.name, device.variant].filter(Boolean).join(" ");
}

export function groupLine(devices: GroupDevice[] | undefined): string {
  return (devices ?? []).map(deviceLabel).filter(Boolean).join(" = ");
}

export function splitEqualsLine(text: string): string[] {
  return text
    .split("=")
    .map((part) => part.trim())
    .filter(Boolean);
}

export function highlightText(text: string, query: string): ReactNode {
  const needle = query.trim();
  if (!needle) {
    return text;
  }
  const lower = text.toLowerCase();
  const match = needle.toLowerCase();
  const parts: ReactNode[] = [];
  let cursor = 0;
  let index = lower.indexOf(match, cursor);
  let key = 0;
  while (index >= 0) {
    if (index > cursor) {
      parts.push(text.slice(cursor, index));
    }
    parts.push(
      createElement("mark", { className: "compat-hl", key: `hl-${key++}` }, text.slice(index, index + needle.length)),
    );
    cursor = index + needle.length;
    index = lower.indexOf(match, cursor);
  }
  if (cursor < text.length) {
    parts.push(text.slice(cursor));
  }
  return createElement(Fragment, null, ...parts);
}
