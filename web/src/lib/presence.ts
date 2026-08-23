import { useEffect } from "react";
import { api } from "./api";
import { getDeviceId } from "./device";

export function usePresence(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const beat = () => {
      void api("/api/v1/presence/heartbeat", {
        method: "POST",
        body: JSON.stringify({ deviceId: getDeviceId(), platform: "WEB", appVersion: "1.0.0" }),
      }).catch(() => {
        /* presence is best-effort */
      });
    };
    beat();
    const timer = window.setInterval(beat, 30000);
    return () => {
      window.clearInterval(timer);
      void api("/api/v1/presence/leave", {
        method: "POST",
        body: JSON.stringify({ deviceId: getDeviceId(), platform: "WEB" }),
      }).catch(() => undefined);
    };
  }, [enabled]);
}
