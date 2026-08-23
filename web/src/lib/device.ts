import { storeGet, storeSet } from "./storage";

export function getDeviceId(): string {
  const existing = storeGet("device");
  if (existing) {
    return existing;
  }
  const created = crypto.randomUUID();
  storeSet("device", created);
  return created;
}
