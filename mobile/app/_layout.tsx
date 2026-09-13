import { useEffect, useState } from "react";
import { AppState, Linking, Platform, Pressable, Text, View } from "react-native";
import { router, Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import * as Application from "expo-application";
import { AuthProvider, useAuth } from "../lib/auth";
import { ThemeProvider, useTheme } from "../lib/theme";
import { selectedWorkspaceId } from "../lib/api";
import { syncNow } from "../lib/offline";
import { startPresence } from "../lib/presence";
import { listenForPush, registerForPush } from "../lib/push";
import { safeAppPath } from "../lib/safePath";
import { applyOtaIfAvailable, checkRelease } from "../lib/updates";

function SyncOnResume() {
  const { user } = useAuth();
  useEffect(() => {
    if (!user) {
      return;
    }
    const stopPresence = startPresence();
    void applyOtaIfAvailable();
    // Registration is retried on resume: permission can be granted from the OS
    // settings screen long after the app first asked, and the Expo token is
    // reissued after some OS updates.
    void registerForPush();
    const stopPush = listenForPush({
      onOpened: (link) => {
        router.push(safeAppPath(link, "/inbox") as never);
      },
    });
    if (!selectedWorkspaceId(user)) {
      return () => {
        stopPush();
        stopPresence();
      };
    }
    void syncNow();
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        void syncNow();
        void registerForPush();
      }
    });
    return () => {
      sub.remove();
      stopPush();
      stopPresence();
    };
  }, [user]);
  return null;
}

function ThemedStack() {
  const { mode } = useTheme();
  const [blocked, setBlocked] = useState(false);
  const [storeUrl, setStoreUrl] = useState<string | null>(null);
  const [notes, setNotes] = useState("");
  const { colors } = useTheme();

  useEffect(() => {
    const platform = Platform.OS === "ios" ? "IOS" : "ANDROID";
    void checkRelease(platform, Number(Application.nativeBuildVersion || 2))
      .then((policy) => {
        if (policy.updateRequired) {
          setBlocked(true);
          setStoreUrl(policy.storeUrl ?? policy.publicOrigin);
          setNotes(policy.notes ?? "Install the latest MobiStack build.");
        }
      })
      .catch(() => undefined);
  }, []);

  if (blocked && storeUrl) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, padding: 28, justifyContent: "center" }}>
        <StatusBar style={mode === "dark" ? "light" : "dark"} />
        <Text style={{ fontSize: 28, fontWeight: "600", color: colors.ink }}>Update required</Text>
        <Text style={{ color: colors.soft, marginTop: 12, marginBottom: 24 }}>{notes}</Text>
        <Pressable
          onPress={() => void Linking.openURL(storeUrl)}
          style={{ backgroundColor: colors.accent, borderRadius: 14, padding: 14, alignItems: "center" }}
        >
          <Text style={{ color: colors.bg, fontWeight: "700" }}>Download the new build</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <>
      <StatusBar style={mode === "dark" ? "light" : "dark"} />
      <SyncOnResume />
      <Stack screenOptions={{ headerShown: false }} />
    </>
  );
}

export default function RootLayout() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ThemedStack />
      </AuthProvider>
    </ThemeProvider>
  );
}
