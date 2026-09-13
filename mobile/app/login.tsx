import { useState } from "react";
import { Image, Pressable, StyleSheet, Text, View } from "react-native";
import { router } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAuth } from "../lib/auth";
import { selectedWorkspaceId } from "../lib/api";
import { copyrightLine } from "../lib/brand";
import { IDENTITY_ISSUER, isOidcConfigured } from "../lib/config";
import { useTheme, type Palette } from "../lib/theme";

/**
 * Hosted Identity only. Password / OTP / magic-link never run in this process.
 * Shop signup happens on Identity (`prompt=create`) or the web console.
 */
export default function LoginScreen() {
  const { loginWithIdentity } = useAuth();
  const { toggle, mode, colors } = useTheme();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const styles = makeStyles(colors);

  if (!isOidcConfigured()) {
    return (
      <SafeAreaView style={styles.safe}>
        <StatusBar style={mode === "dark" ? "light" : "dark"} />
        <View style={styles.identityBody}>
          <Text style={styles.title}>MobiStack</Text>
          <Text style={styles.subtitle}>
            This build has no Identity issuer. Set EXPO_PUBLIC_IDENTITY_ISSUER (see eas.json / .env.example)
            and rebuild. In-app password login has been removed.
          </Text>
        </View>
        <Text style={styles.footer}>{copyrightLine()}</Text>
      </SafeAreaView>
    );
  }

  async function onContinue() {
    setBusy(true);
    setError(null);
    try {
      const user = await loginWithIdentity();
      router.replace(selectedWorkspaceId(user) ? "/(tabs)/home" : "/workspaces");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in did not complete.");
    } finally {
      setBusy(false);
    }
  }

  async function onCreateAccount() {
    setBusy(true);
    setError(null);
    try {
      const user = await loginWithIdentity({ prompt: "create" });
      router.replace(selectedWorkspaceId(user) ? "/(tabs)/home" : "/workspaces");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-up did not complete.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style={mode === "dark" ? "light" : "dark"} />
      <View style={styles.topRow}>
        <Image source={require("../assets/icon.png")} style={styles.logo} />
        <Pressable onPress={toggle} hitSlop={12}>
          <Text style={styles.link}>{mode === "dark" ? "Light" : "Dark"}</Text>
        </Pressable>
      </View>
      <View style={styles.identityBody}>
        <Text style={styles.title}>MobiStack</Text>
        <Text style={styles.subtitle}>
          Sign in with your Prabhix account. The same login works across OneOps, Admin, and Mailroom.
        </Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Pressable
          style={[styles.primary, busy && styles.primaryDisabled]}
          disabled={busy}
          onPress={() => void onContinue()}
        >
          <Text style={styles.primaryLabel}>{busy ? "Opening…" : "Continue to sign in"}</Text>
        </Pressable>
        <Pressable disabled={busy} onPress={() => void onCreateAccount()} hitSlop={12}>
          <Text style={styles.link}>Create an account</Text>
        </Pressable>
        {__DEV__ ? (
          <Text style={styles.devHint}>Issuer: {IDENTITY_ISSUER}</Text>
        ) : null}
      </View>
      <Text style={styles.footer}>{copyrightLine()}</Text>
    </SafeAreaView>
  );
}

function makeStyles(colors: Palette) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: colors.bg },
    topRow: {
      flexDirection: "row",
      justifyContent: "space-between",
      alignItems: "center",
      paddingHorizontal: 24,
      paddingTop: 8,
    },
    logo: { width: 40, height: 40, borderRadius: 10 },
    title: { fontSize: 32, fontWeight: "600", color: colors.ink, marginTop: 24, letterSpacing: -0.6 },
    subtitle: { color: colors.soft, marginTop: 8, marginBottom: 24, lineHeight: 22 },
    identityBody: { flex: 1, padding: 24, justifyContent: "center" },
    primary: { backgroundColor: colors.accent, borderRadius: 14, padding: 16, alignItems: "center", marginTop: 8 },
    primaryDisabled: { opacity: 0.6 },
    primaryLabel: { color: colors.accentInk, fontWeight: "700", fontSize: 16 },
    link: { color: colors.accent, marginTop: 16 },
    error: { color: colors.bad, marginBottom: 8 },
    footer: { color: colors.soft, textAlign: "center", marginBottom: 24, fontSize: 12 },
    devHint: { color: colors.faint, marginTop: 24, fontSize: 11 },
  });
}
