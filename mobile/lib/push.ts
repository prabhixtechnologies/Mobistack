import { Platform } from "react-native";
import Constants from "expo-constants";
import * as Application from "expo-application";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import { api } from "./api";
import { getDeviceId } from "./device";

/**
 * Decides what happens when a push lands while the app is open. Without this
 * the OS shows nothing in the foreground, which reads as "push is broken" even
 * when delivery worked.
 */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

/** Android needs a channel before any notification can be shown at all. */
const CHANNEL = "mobistack-default";

export type PushOutcome =
  | { state: "registered"; token: string }
  | { state: "denied" }
  | { state: "unavailable"; reason: string };

let lastRegistered: string | null = null;

/** Reported to the server so support can tell which build a phone is on. */
export function clientBuild(): { appVersion: string; nativeBuild: number } {
  return {
    appVersion: Constants.expoConfig?.version ?? "0.0.0",
    nativeBuild: Number(Application.nativeBuildVersion ?? 0) || 0,
  };
}

/**
 * Expo needs the EAS project to route a token. A standalone build carries it in
 * the manifest; a bare dev client may not, so the environment can supply it.
 */
function projectId(): string | null {
  const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
  const eas = (Constants as unknown as { easConfig?: { projectId?: string } | null }).easConfig;
  return extra?.eas?.projectId ?? eas?.projectId ?? process.env.EXPO_PUBLIC_EAS_PROJECT_ID ?? null;
}

async function ensureChannel(): Promise<void> {
  if (Platform.OS !== "android") {
    return;
  }
  await Notifications.setNotificationChannelAsync(CHANNEL, {
    name: "Shop alerts",
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: "#7C3AED",
  });
}

/**
 * Asks once, then respects the answer. Android 13 and iOS both require an
 * explicit grant, and a token cannot be issued without it.
 */
async function ensurePermission(): Promise<boolean> {
  const existing = await Notifications.getPermissionsAsync();
  if (existing.granted) {
    return true;
  }
  if (existing.canAskAgain === false) {
    return false;
  }
  const asked = await Notifications.requestPermissionsAsync();
  return asked.granted;
}

/**
 * Registers this phone for push and tells the server where to reach it.
 *
 * <p>Every failure mode is reported rather than swallowed: a shopkeeper who
 * never receives an alert needs to know whether they denied permission, are on
 * an emulator, or the build is missing its push credentials.
 */
export async function registerForPush(): Promise<PushOutcome> {
  if (!Device.isDevice) {
    return { state: "unavailable", reason: "Push notifications only work on a real phone, not a simulator." };
  }
  try {
    await ensureChannel();
    if (!(await ensurePermission())) {
      return { state: "denied" };
    }
    const id = projectId();
    const token = await Notifications.getExpoPushTokenAsync(id ? { projectId: id } : undefined);
    const deviceId = await getDeviceId();
    // The server keys tokens by device, so re-sending an unchanged one is only
    // wasted work — but a changed one must go up immediately or alerts stop.
    if (lastRegistered !== `${deviceId}:${token.data}`) {
      await api("/api/v1/inbox/push-token", {
        method: "POST",
        body: JSON.stringify({
          deviceId,
          platform: Platform.OS === "ios" ? "IOS" : "ANDROID",
          expoPushToken: token.data,
          ...clientBuild(),
        }),
      });
      lastRegistered = `${deviceId}:${token.data}`;
    }
    return { state: "registered", token: token.data };
  } catch (err) {
    const reason = err instanceof Error ? err.message : "Push registration failed.";
    return { state: "unavailable", reason };
  }
}

/** Forgets the cached token so the next sign-in re-registers from scratch. */
export function forgetPushRegistration(): void {
  lastRegistered = null;
}

export interface PushListeners {
  /** A push arrived. Used to refresh the inbox badge without a poll. */
  onReceived?: () => void;
  /** The shopkeeper tapped a push. `link` is an in-app path when present. */
  onOpened?: (link: string | null) => void;
}

function linkFrom(data: unknown): string | null {
  if (data && typeof data === "object" && "link" in data) {
    const link = (data as { link?: unknown }).link;
    return typeof link === "string" && link.trim() ? link.trim() : null;
  }
  return null;
}

export function listenForPush(listeners: PushListeners): () => void {
  const received = Notifications.addNotificationReceivedListener(() => {
    listeners.onReceived?.();
  });
  const opened = Notifications.addNotificationResponseReceivedListener((response) => {
    listeners.onOpened?.(linkFrom(response.notification.request.content.data));
  });
  return () => {
    received.remove();
    opened.remove();
  };
}

/** Clears the app icon badge, called when the inbox is read. */
export async function clearBadge(): Promise<void> {
  try {
    await Notifications.setBadgeCountAsync(0);
  } catch {
    // Badges are unsupported on some Android launchers; nothing to recover.
  }
}
