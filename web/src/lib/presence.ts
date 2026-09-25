import { useEffect } from "react";
import { api } from "./api";
import { getDeviceId } from "./device";

export function usePresence(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const beat = () => {
      if (document.hidden) {
        return;
      }
      void api("/api/v1/mobistack/presence/heartbeat", {
        method: "POST",
        body: JSON.stringify({ deviceId: getDeviceId(), platform: "WEB", appVersion: "1.0.0" }),
      }).catch(() => {
        /* presence is best-effort */
      });
    };
    beat();
    const timer = window.setInterval(beat, 30000);
    const onVis = () => {
      if (!document.hidden) {
        beat();
      }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVis);
      void api("/api/v1/mobistack/presence/leave", {
        method: "POST",
        body: JSON.stringify({ deviceId: getDeviceId(), platform: "WEB" }),
      }).catch(() => undefined);
    };
  }, [enabled]);
}
