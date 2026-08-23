import { useEffect, useState, type ReactNode } from "react";
import {
  Image,
  KeyboardAvoidingView,
  Linking,
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
import { BRAND, copyrightLine } from "../lib/brand";
import { getDeviceId } from "../lib/device";
import { useTheme } from "../lib/theme";

type Method = "password" | "magic" | "email" | "phone" | "whatsapp" | "register" | "forgot";

const REMEMBER_KEY = "mobistack.remember-email";

export default function LoginScreen() {
  const { login, acceptSession } = useAuth();
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
            body: JSON.stringify({ phone, channel: method === "whatsapp" ? "WHATSAPP" : "SMS" }),
          });
          setNotice("A one-time code was sent to that number.");
        } else {
          const auth = await api<AuthResponse>(`/api/v1/auth/${channel}/verify`, {
            method: "POST",
            body: JSON.stringify({ phone, code }),
          });
          await go(await acceptSession(auth));
        }
      } else if (method === "forgot") {
        await api("/api/v1/auth/forgot-password", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice("If that email is registered, a reset link was sent.");
      } else {
        if (!acceptedTerms) {
          throw new Error("Accept the terms to create an account.");
        }
        const auth = await api<AuthResponse>("/api/v1/auth/register", {
          method: "POST",
          body: JSON.stringify({ fullName, email, password, phone }),
        });
        await go(await acceptSession(auth));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not continue");
    } finally {
      setBusy(false);
    }
  }

  async function startGoogle() {
    setError(null);
    setNotice(null);
    try {
      const start = await api<{ status: string; authorizationUrl?: string; hint?: string }>(
        `/api/v1/auth/sso/google/start?redirectUri=${encodeURIComponent("mobistack://login?sso=google")}`,
      );
      if (start.status === "READY" && start.authorizationUrl) {
        await Linking.openURL(start.authorizationUrl);
        return;
      }
      setNotice("Google sign-in is not configured for this environment.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start Google");
    }
  }

  useEffect(() => {
    const googleCode = typeof params.code === "string" ? params.code : undefined;
    if (params.sso !== "google" || !googleCode) {
      return;
    }
    setBusy(true);
    void getDeviceId()
      .then((deviceId) =>
        api<AuthResponse>("/api/v1/auth/sso/google", {
          method: "POST",
          body: JSON.stringify({
            code: googleCode,
            redirectUri: "mobistack://login?sso=google",
            deviceId,
          }),
        }),
      )
      .then(async (auth) => {
        await go(await acceptSession(auth));
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Google sign-in failed");
      })
      .finally(() => setBusy(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.code, params.sso]);

  const heading =
    method === "register"
      ? "Create your account"
      : method === "forgot"
        ? "Forgot password"
        : method === "magic"
          ? "Sign in with a link"
          : method === "email"
            ? "Sign in with email code"
            : method === "phone"
              ? "Sign in with SMS"
              : method === "whatsapp"
                ? "Sign in with WhatsApp"
                : "Welcome back";

  const subtitle =
    method === "register"
      ? "Join an existing shop after an owner approves you."
      : method === "forgot"
        ? "We will email reset instructions if that account exists."
        : "Please sign in to continue.";

  const submitLabel = busy
    ? "Working…"
    : method === "magic"
      ? "Send magic link"
      : method === "register"
        ? "Create account"
        : method === "forgot"
          ? "Send reset"
          : code
            ? "Verify and sign in"
            : method === "email" || method === "phone" || method === "whatsapp"
              ? "Send code"
              : "Sign in →";

  const showEmail = method === "password" || method === "magic" || method === "email" || method === "register" || method === "forgot";
  const showPasswordField = method === "password" || method === "register";
  const showPhone = method === "phone" || method === "whatsapp" || method === "register";
  const showCode = method === "email" || method === "phone" || method === "whatsapp";
  const showAlts = method !== "register" && method !== "forgot";
  const showCreateUser = method !== "register" && method !== "forgot";
  const legal = (path: string) => void Linking.openURL(`${BRAND.publicOrigin}${path}`);

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar style="light" />
      <View style={styles.top}>
        <Pressable style={styles.iconBtn} onPress={toggle} accessibilityLabel="Toggle color theme">
          <Text style={styles.iconBtnText}>{mode === "light" ? "☾" : "☀"}</Text>
        </Pressable>
        <View style={styles.tls}>
          <Text style={styles.tlsText}>TLS</Text>
        </View>
      </View>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView contentContainerStyle={styles.wrap} keyboardShouldPersistTaps="handled">
          <View style={styles.card}>
            <View style={styles.brand}>
              <Image source={require("../assets/logo.png")} style={styles.logo} />
              <Text style={styles.brandName}>{BRAND.product}</Text>
            </View>
            <Text style={styles.heading}>{heading}</Text>
            <Text style={styles.welcome}>{subtitle}</Text>

            {method === "register" && (
              <Field label="Full name">
                <TextInput
                  style={styles.input}
                  value={fullName}
                  onChangeText={setFullName}
                  placeholder="Your name"
                  placeholderTextColor={styles.placeholder.color}
                  autoComplete="name"
                />
              </Field>
            )}
            {showEmail && (
              <Field label="Email">
                <TextInput
                  style={styles.input}
                  value={email}
                  onChangeText={setEmail}
                  autoCapitalize="none"
                  keyboardType="email-address"
                  placeholder="you@prabhixtechnologies.com"
                  placeholderTextColor={styles.placeholder.color}
                  autoComplete="email"
                />
              </Field>
            )}
            {showPasswordField && (
              <Field label="Password">
                <View style={styles.passwordRow}>
                  <TextInput
                    style={[styles.input, { flex: 1, marginBottom: 0, borderWidth: 0, backgroundColor: "transparent" }]}
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry={!passwordVisible}
                    placeholder="Enter your password"
                    placeholderTextColor={styles.placeholder.color}
                    autoComplete={method === "register" ? "new-password" : "password"}
                  />
                  <Pressable onPress={() => setPasswordVisible((open) => !open)} hitSlop={8}>
                    <Text style={styles.link}>{passwordVisible ? "Hide" : "Show"}</Text>
                  </Pressable>
                </View>
              </Field>
            )}
            {showPhone && (
              <Field label={method === "whatsapp" ? "WhatsApp number" : "Mobile number"}>
                <TextInput
                  style={styles.input}
                  value={phone}
                  onChangeText={setPhone}
                  keyboardType="phone-pad"
                  placeholder="+91 98XXXXXXXX"
                  placeholderTextColor={styles.placeholder.color}
                />
              </Field>
            )}
            {showCode && (
              <Field label="One-time code">
                <TextInput
                  style={styles.input}
                  value={code}
                  onChangeText={setCode}
                  keyboardType="number-pad"
                  placeholder="Leave blank to request a code"
                  placeholderTextColor={styles.placeholder.color}
                />
              </Field>
            )}

            {method === "password" && (
              <View style={styles.row}>
                <Pressable style={styles.remember} onPress={() => setRemember((value) => !value)}>
                  <View style={[styles.check, remember && styles.checkOn]} />
                  <Text style={styles.rememberText}>Remember me</Text>
                </Pressable>
                <Pressable onPress={() => choose("forgot")}>
                  <Text style={styles.link}>Forgot password?</Text>
                </Pressable>
              </View>
            )}
            {method === "forgot" && (
              <Pressable onPress={() => choose("password")}>
                <Text style={styles.link}>Back to sign in</Text>
              </Pressable>
            )}
            {method === "register" && (
              <Pressable style={styles.remember} onPress={() => setAcceptedTerms((value) => !value)}>
                <View style={[styles.check, acceptedTerms && styles.checkOn]} />
                <Text style={styles.rememberText}>
                  I agree to the Terms, Privacy Policy, and Refunds. An owner must approve you before you can work in a
                  shop.
                </Text>
              </Pressable>
            )}

            {notice ? <Text style={styles.notice}>{notice}</Text> : null}
            {error ? <Text style={styles.error}>{error}</Text> : null}

            <View style={styles.actions}>
              <Pressable style={[styles.btn, styles.btnPrimary, showCreateUser || method === "register" ? styles.btnHalf : undefined]} onPress={() => void onContinue()}>
                <Text style={styles.btnPrimaryText}>{submitLabel}</Text>
              </Pressable>
              {showCreateUser && (
                <Pressable style={[styles.btn, styles.btnSecondary, styles.btnHalf]} onPress={() => choose("register")}>
                  <Text style={styles.btnSecondaryText}>Create user</Text>
                </Pressable>
              )}
              {method === "register" && (
                <Pressable style={[styles.btn, styles.btnSecondary, styles.btnHalf]} onPress={() => choose("password")}>
                  <Text style={styles.btnSecondaryText}>Sign in</Text>
                </Pressable>
              )}
            </View>

            {showAlts && (
              <>
                <View style={styles.rule}>
                  <View style={styles.ruleLine} />
                  <Text style={styles.ruleText}>Or continue with</Text>
                  <View style={styles.ruleLine} />
                </View>
                <View style={styles.alts}>
                  <Alt label="Google" on={false} onPress={() => void startGoogle()} />
                  <Alt label="Magic link" on={method === "magic"} onPress={() => choose("magic")} />
                  <Alt label="Email code" on={method === "email"} onPress={() => choose("email")} />
                  <Alt label="Phone" on={method === "phone" || method === "whatsapp"} onPress={() => choose(method === "whatsapp" ? "whatsapp" : "phone")} />
                </View>
                {(method === "phone" || method === "whatsapp") && (
                  <View style={styles.channel}>
                    <Pressable style={[styles.channelBtn, method === "phone" && styles.channelOn]} onPress={() => choose("phone")}>
                      <Text style={method === "phone" ? styles.channelOnText : styles.channelText}>SMS</Text>
                    </Pressable>
                    <Pressable style={[styles.channelBtn, method === "whatsapp" && styles.channelOn]} onPress={() => choose("whatsapp")}>
                      <Text style={method === "whatsapp" ? styles.channelOnText : styles.channelText}>WhatsApp</Text>
                    </Pressable>
                  </View>
                )}
                {(method === "magic" || method === "email" || method === "phone" || method === "whatsapp") && (
                  <Pressable onPress={() => choose("password")}>
                    <Text style={[styles.link, styles.center]}>Use email and password</Text>
                  </Pressable>
                )}
              </>
            )}

            <Text style={styles.legal}>{copyrightLine()}</Text>
            <View style={styles.legalLinks}>
              <Pressable onPress={() => legal("/privacy")}><Text style={styles.legalLink}>Privacy</Text></Pressable>
              <Pressable onPress={() => legal("/terms")}><Text style={styles.legalLink}>Terms</Text></Pressable>
              <Pressable onPress={() => legal("/refunds")}><Text style={styles.legalLink}>Refunds</Text></Pressable>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  const { mode } = useTheme();
  return (
    <View style={{ marginBottom: 12 }}>
      <Text style={{ fontSize: 13, fontWeight: "600", color: mode === "dark" ? "#C8C2B6" : "#3F3A47", marginBottom: 6 }}>
        {label}
      </Text>
      {children}
    </View>
  );
}

function Alt({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  const { mode } = useTheme();
  const dark = mode === "dark";
  return (
    <Pressable
      onPress={onPress}
      style={{
        flexBasis: "48%",
        flexGrow: 1,
        minHeight: 40,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: on ? "#7C3AED" : dark ? "rgba(244,241,234,0.1)" : "#ECE7F3",
        backgroundColor: on ? "#F5F0FF" : dark ? "#241F2C" : "#FFF",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text style={{ fontWeight: "600", fontSize: 13, color: dark ? "#F4F1EA" : "#2D2836" }}>{label}</Text>
    </Pressable>
  );
}

function makeStyles(dark: boolean) {
  const ink = dark ? "#F4F1EA" : "#16131C";
  const faint = dark ? "#B7B2A4" : "#7A7384";
  const field = dark ? "#141218" : "#F7F5FA";
  const line = dark ? "rgba(244,241,234,0.12)" : "#E6E0EE";
  return StyleSheet.create({
    screen: { flex: 1, backgroundColor: "#1A0B2E" },
    wrap: { padding: 16, paddingBottom: 28, flexGrow: 1, justifyContent: "center" },
    top: { flexDirection: "row", justifyContent: "flex-end", alignItems: "center", gap: 8, paddingHorizontal: 16, paddingTop: 4 },
    iconBtn: {
      width: 40,
      height: 40,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: "rgba(255,255,255,0.18)",
      backgroundColor: "rgba(255,255,255,0.08)",
      alignItems: "center",
      justifyContent: "center",
    },
    iconBtnText: { color: "#F8F5FF", fontSize: 16 },
    tls: {
      minHeight: 40,
      paddingHorizontal: 12,
      borderRadius: 999,
      backgroundColor: "rgba(52,211,153,0.12)",
      alignItems: "center",
      justifyContent: "center",
    },
    tlsText: { color: "#86EFAC", fontSize: 11, fontWeight: "700", letterSpacing: 1 },
    card: {
      backgroundColor: dark ? "#1A1622" : "#FFF",
      borderRadius: 24,
      padding: 22,
      borderWidth: 1,
      borderColor: dark ? "rgba(255,255,255,0.08)" : "rgba(255,255,255,0.55)",
    },
    brand: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10, marginBottom: 14 },
    logo: { width: 36, height: 36, borderRadius: 11 },
    brandName: { fontSize: 17, fontWeight: "700", color: dark ? "#DDD6FE" : "#4C1D95", letterSpacing: -0.4 },
    heading: { fontSize: 26, fontWeight: "700", letterSpacing: -0.6, color: ink, textAlign: "center" },
    welcome: { color: faint, fontSize: 13, textAlign: "center", marginTop: 4, marginBottom: 16 },
    input: {
      backgroundColor: field,
      borderColor: line,
      borderWidth: 1,
      borderRadius: 12,
      paddingHorizontal: 12,
      paddingVertical: 12,
      color: ink,
      fontSize: 15,
      marginBottom: 0,
    },
    placeholder: { color: "#A8A1B3" },
    passwordRow: {
      flexDirection: "row",
      alignItems: "center",
      gap: 10,
      backgroundColor: field,
      borderColor: line,
      borderWidth: 1,
      borderRadius: 12,
      paddingRight: 12,
    },
    row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 8 },
    remember: { flexDirection: "row", alignItems: "flex-start", gap: 8, flex: 1, paddingRight: 12 },
    check: { width: 16, height: 16, borderRadius: 5, borderWidth: 1.5, borderColor: "#C9C0D6", backgroundColor: dark ? "#141218" : "#FFF", marginTop: 2 },
    checkOn: { backgroundColor: "#6D28D9", borderColor: "#6D28D9" },
    rememberText: { color: faint, fontSize: 13, flex: 1, lineHeight: 18 },
    link: { color: "#6D28D9", fontWeight: "600", fontSize: 13 },
    center: { textAlign: "center", marginTop: 8 },
    notice: { color: "#166534", backgroundColor: "#F0FDF4", padding: 10, borderRadius: 10, overflow: "hidden", marginBottom: 8 },
    error: { color: "#991B1B", backgroundColor: "#FEF2F2", padding: 10, borderRadius: 10, overflow: "hidden", marginBottom: 8 },
    actions: { flexDirection: "row", gap: 10, marginTop: 8 },
    btn: { borderRadius: 12, minHeight: 44, alignItems: "center", justifyContent: "center", paddingHorizontal: 12 },
    btnHalf: { flex: 1 },
    btnPrimary: { backgroundColor: "#6D28D9" },
    btnPrimaryText: { color: "#FFF", fontWeight: "700", fontSize: 14 },
    btnSecondary: { backgroundColor: dark ? "rgba(196,181,253,0.08)" : "#F4F0FB" },
    btnSecondaryText: { color: dark ? "#DDD6FE" : "#5B21B6", fontWeight: "700", fontSize: 14 },
    rule: { flexDirection: "row", alignItems: "center", gap: 10, marginTop: 16, marginBottom: 12 },
    ruleLine: { flex: 1, height: 1, backgroundColor: dark ? "rgba(244,241,234,0.1)" : "#EEEAF4" },
    ruleText: { color: "#9A93A3", fontSize: 11, fontWeight: "700", letterSpacing: 0.8, textTransform: "uppercase" },
    alts: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
    channel: { flexDirection: "row", gap: 8, marginTop: 8 },
    channelBtn: {
      flex: 1,
      minHeight: 38,
      borderRadius: 999,
      borderWidth: 1,
      borderColor: dark ? "rgba(244,241,234,0.12)" : "#ECE7F3",
      backgroundColor: dark ? "#141218" : "#F7F5FA",
      alignItems: "center",
      justifyContent: "center",
    },
    channelOn: { backgroundColor: "#5B21B6", borderColor: "#5B21B6" },
    channelText: { fontWeight: "700", color: ink, fontSize: 13 },
    channelOnText: { fontWeight: "700", color: "#FFF", fontSize: 13 },
    legal: { color: "#9A93A3", fontSize: 12, textAlign: "center", marginTop: 18, lineHeight: 18 },
    legalLinks: { flexDirection: "row", justifyContent: "center", gap: 14, marginTop: 6 },
    legalLink: { color: faint, fontSize: 12 },
  });
}
