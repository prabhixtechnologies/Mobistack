import { AppState, Platform } from "react-native";
import { api } from "./api";
import { getDeviceId } from "./device";

async function registerPush(): Promise<void> {
  try {
    const Notifications = await import("expo-notifications");
    const token = await Notifications.getExpoPushTokenAsync();
    await api("/api/v1/inbox/push-token", {
      method: "POST",
      body: JSON.stringify({
        deviceId: await getDeviceId(),
        platform: Platform.OS === "ios" ? "IOS" : "ANDROID",
        expoPushToken: token.data,
        appVersion: "1.0.0",
        nativeBuild: 1,
      }),
    });
  } catch {
    /* push is optional until a native rebuild includes the permission */
  }
}

export async function heartbeat(): Promise<void> {
  try {
    await api("/api/v1/presence/heartbeat", {
      method: "POST",
      body: JSON.stringify({
        deviceId: await getDeviceId(),
        platform: Platform.OS === "ios" ? "IOS" : "ANDROID",
        appVersion: "1.0.0",
        nativeBuild: 1,
      }),
    });
  } catch {
    /* best-effort */
  }
}

export function startPresence(): () => void {
  void heartbeat();
  void registerPush();
  const timer = setInterval(() => {
    void heartbeat();
  }, 30000);
  const sub = AppState.addEventListener("change", (state) => {
    if (state === "active") {
      void heartbeat();
    }
  });
  return () => {
    clearInterval(timer);
    sub.remove();
  };
}
