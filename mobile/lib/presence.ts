import { AppState, Platform } from "react-native";
import { api } from "./api";
import { getDeviceId } from "./device";
import { clientBuild } from "./push";

export async function heartbeat(): Promise<void> {
  try {
    await api("/api/v1/presence/heartbeat", {
      method: "POST",
      body: JSON.stringify({
        deviceId: await getDeviceId(),
        platform: Platform.OS === "ios" ? "IOS" : "ANDROID",
        ...clientBuild(),
      }),
    });
  } catch {
    /* best-effort */
  }
}

export function startPresence(): () => void {
  void heartbeat();
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
