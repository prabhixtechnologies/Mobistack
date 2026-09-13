import { useEffect, useState } from "react";
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { SafeAreaView } from "react-native-safe-area-context";
import * as SecureStore from "expo-secure-store";
import { useAuth } from "../lib/auth";
import { api, selectedWorkspaceId, type AuthResponse, type AuthUser } from "../lib/api";
import { copyrightLine } from "../lib/brand";
import { isOidcEnabled } from "../lib/config";
import { getDeviceId } from "../lib/device";
import { useTheme } from "../lib/theme";

type Method = "password" | "magic" | "email" | "phone" | "whatsapp" | "register" | "forgot";

const REMEMBER_KEY = "mobistack.remember-email";

export default function LoginScreen() {
  if (isOidcEnabled()) {
    return <IdentityLogin />;
  }
  return <LegacyLogin />;
}

/**
 * Hosted Identity: same account as OneOps / Admin / Mailroom. Password never touches this process.
 */
function IdentityLogin() {
  const { loginWithIdentity } = useAuth();
  const { toggle, mode } = useTheme();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const styles = makeStyles(mode === "dark");

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
      </View>
      <Text style={styles.footer}>{copyrightLine()}</Text>
    </SafeAreaView>
  );
}

/** Local/dev builds without {@code EXPO_PUBLIC_IDENTITY_ISSUER}. */
function LegacyLogin() {
  const { login, acceptSession, registerShop } = useAuth();
  const { toggle, mode } = useTheme();
  const params = useLocalSearchParams<{ code?: string; sso?: string }>();
  const [method, setMethod] = useState<Method>("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [remember, setRemember] = useState(false);
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [shopName, setShopName] = useState("");
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const styles = makeStyles(mode === "dark");

  useEffect(() => {
    void SecureStore.getItemAsync(REMEMBER_KEY).then((stored) => {
      if (stored) {
        setEmail(stored);
        setRemember(true);
      }
    });
  }, []);

  function choose(next: Method) {
    setMethod(next);
    setError(null);
    setNotice(null);
    setCode("");
  }

  async function go(user: AuthUser) {
    router.replace(selectedWorkspaceId(user) ? "/(tabs)/home" : "/workspaces");
  }

  async function persistRemember() {
    if (remember) {
      await SecureStore.setItemAsync(REMEMBER_KEY, email);
    } else {
      await SecureStore.deleteItemAsync(REMEMBER_KEY);
    }
  }

  async function onContinue() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (method === "password") {
        await persistRemember();
        await go(await login(email, password));
      } else if (method === "magic") {
        await api("/api/v1/auth/magic-link", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice("If that email is registered, a sign-in link was sent.");
      } else if (method === "email") {
        if (!code) {
          await api("/api/v1/auth/email-otp", {
            method: "POST",
            body: JSON.stringify({ email }),
          });
          setNotice("If that email is registered, a one-time code was sent.");
        } else {
          const auth = await api<AuthResponse>("/api/v1/auth/email-otp/verify", {
            method: "POST",
            body: JSON.stringify({ email, code }),
          });
          await go(await acceptSession(auth));
        }
      } else if (method === "phone" || method === "whatsapp") {
        const channel = method === "whatsapp" ? "whatsapp" : "phone";
        if (!code) {
          await api(`/api/v1/auth/${channel}/start`, {
            method: "POST",
            body: JSON.stringify({ phone }),
          });
          setNotice("If that number is registered, a code was sent.");
        } else {
          const auth = await api<AuthResponse>(`/api/v1/auth/${channel}/verify`, {
            method: "POST",
            body: JSON.stringify({ phone, code, deviceId: await getDeviceId() }),
          });
          await go(await acceptSession(auth));
        }
      } else if (method === "forgot") {
        await api("/api/v1/auth/forgot-password", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice("If that email is registered, reset instructions were sent.");
      } else if (method === "register") {
        if (!acceptedTerms) {
          setError("Accept the terms to create a shop.");
          return;
        }
        await go(await registerShop({
          shopName,
          ownerName: fullName,
          email,
          password,
          phone: phone || undefined,
        }));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (params.code && typeof params.code === "string") {
      setCode(params.code);
      setMethod("magic");
    }
  }, [params.code]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style={mode === "dark" ? "light" : "dark"} />
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
          <View style={styles.topRow}>
            <Image source={require("../assets/icon.png")} style={styles.logo} />
            <Pressable onPress={toggle} hitSlop={12}>
              <Text style={styles.link}>{mode === "dark" ? "Light" : "Dark"}</Text>
            </Pressable>
          </View>
          <Text style={styles.title}>MobiStack</Text>
          <Text style={styles.subtitle}>Dev build — Identity issuer not set</Text>

          <View style={styles.tabs}>
            {(["password", "magic", "email", "register"] as Method[]).map((m) => (
              <Pressable key={m} onPress={() => choose(m)} style={[styles.tab, method === m && styles.tabOn]}>
                <Text style={[styles.tabLabel, method === m && styles.tabLabelOn]}>{m}</Text>
              </Pressable>
            ))}
          </View>

          {(method === "password" || method === "magic" || method === "email" || method === "forgot" || method === "register") && (
            <TextInput
              style={styles.input}
              autoCapitalize="none"
              keyboardType="email-address"
              placeholder="Email"
              placeholderTextColor={styles.placeholder.color}
              accessibilityLabel="Email"
              value={email}
              onChangeText={setEmail}
            />
          )}
          {(method === "password" || method === "register") && (
            <TextInput
              style={styles.input}
              secureTextEntry={!passwordVisible}
              placeholder="Password"
              placeholderTextColor={styles.placeholder.color}
              accessibilityLabel="Password"
              value={password}
              onChangeText={setPassword}
            />
          )}
          {method === "register" && (
            <>
              <TextInput style={styles.input} placeholder="Shop name" placeholderTextColor={styles.placeholder.color} accessibilityLabel="Shop name" value={shopName} onChangeText={setShopName} />
              <TextInput style={styles.input} placeholder="Your name" placeholderTextColor={styles.placeholder.color} accessibilityLabel="Your name" value={fullName} onChangeText={setFullName} />
              <TextInput style={styles.input} placeholder="Phone (optional)" placeholderTextColor={styles.placeholder.color} accessibilityLabel="Phone" keyboardType="phone-pad" value={phone} onChangeText={setPhone} />
              <Pressable onPress={() => setAcceptedTerms((v) => !v)}>
                <Text style={styles.link}>{acceptedTerms ? "✓" : "○"} Accept terms</Text>
              </Pressable>
            </>
          )}
          {(method === "email" || method === "phone" || method === "whatsapp") && (
            <TextInput style={styles.input} placeholder="Code" placeholderTextColor={styles.placeholder.color} accessibilityLabel="One-time code" keyboardType="number-pad" value={code} onChangeText={setCode} />
          )}

          {error ? <Text style={styles.error}>{error}</Text> : null}
          {notice ? <Text style={styles.notice}>{notice}</Text> : null}

          <Pressable style={[styles.primary, busy && styles.primaryDisabled]} disabled={busy} onPress={() => void onContinue()}>
            <Text style={styles.primaryLabel}>{busy ? "Working…" : "Continue"}</Text>
          </Pressable>

          <Pressable onPress={() => choose("forgot")}>
            <Text style={styles.link}>Forgot password</Text>
          </Pressable>
          <Text style={styles.footer}>{copyrightLine()}</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function makeStyles(dark: boolean) {
  const ink = dark ? "#F8FAFC" : "#0F172A";
  const soft = dark ? "#94A3B8" : "#64748B";
  const bg = dark ? "#0B1020" : "#F8FAFC";
  const card = dark ? "#151B2E" : "#FFFFFF";
  const border = dark ? "#243047" : "#E2E8F0";
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: bg },
    scroll: { padding: 24, paddingBottom: 48 },
    topRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
    logo: { width: 40, height: 40, borderRadius: 10 },
    title: { fontSize: 32, fontWeight: "700", color: ink, marginTop: 24 },
    subtitle: { color: soft, marginTop: 8, marginBottom: 24, lineHeight: 22 },
    identityBody: { flex: 1, padding: 24, justifyContent: "center" },
    tabs: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 16 },
    tab: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999, backgroundColor: card, borderWidth: 1, borderColor: border },
    tabOn: { backgroundColor: ink },
    tabLabel: { color: soft, fontSize: 13, textTransform: "capitalize" },
    tabLabelOn: { color: bg },
    input: {
      backgroundColor: card,
      borderWidth: 1,
      borderColor: border,
      borderRadius: 12,
      paddingHorizontal: 14,
      paddingVertical: 12,
      color: ink,
      marginBottom: 12,
    },
    placeholder: { color: soft },
    primary: { backgroundColor: ink, borderRadius: 14, padding: 16, alignItems: "center", marginTop: 8 },
    primaryDisabled: { opacity: 0.6 },
    primaryLabel: { color: bg, fontWeight: "700", fontSize: 16 },
    link: { color: "#7C3AED", marginTop: 16 },
    error: { color: "#DC2626", marginBottom: 8 },
    notice: { color: soft, marginBottom: 8 },
    footer: { color: soft, textAlign: "center", marginTop: 32, fontSize: 12 },
  });
}
