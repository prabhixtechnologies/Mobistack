import { api } from "./api";

export interface ReleasePolicy {
  platform: string;
  minNativeBuild: number;
  latestNativeBuild: number;
  forceNativeUpdate: boolean;
  updateRequired: boolean;
  otaChannel: string;
  storeUrl?: string;
  notes?: string;
  publicOrigin: string;
}

export async function checkRelease(platform: "ANDROID" | "IOS", build: number): Promise<ReleasePolicy> {
  return api<ReleasePolicy>(`/api/v1/public/app-release?platform=${platform}&build=${build}`);
}

export async function applyOtaIfAvailable(): Promise<string | null> {
  try {
    const Updates = await import("expo-updates");
    if (!Updates.isEnabled) {
      return null;
    }
    const result = await Updates.checkForUpdateAsync();
    if (result.isAvailable) {
      await Updates.fetchUpdateAsync();
      await Updates.reloadAsync();
      return "Applied a live update.";
    }
  } catch {
    return null;
  }
  return null;
}
