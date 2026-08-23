import * as SecureStore from "expo-secure-store";

const KEY = "fixflow.device";

export async function getDeviceId(): Promise<string> {
  const existing = await SecureStore.getItemAsync(KEY);
  if (existing) {
    return existing;
  }
  const created = `ff-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  await SecureStore.setItemAsync(KEY, created);
  return created;
}
