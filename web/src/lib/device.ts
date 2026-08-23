const DEVICE_KEY = "fixflow.device";

export function getDeviceId(): string {
  const existing = localStorage.getItem(DEVICE_KEY);
  if (existing) {
    return existing;
  }
  const created = crypto.randomUUID();
  localStorage.setItem(DEVICE_KEY, created);
  return created;
}
