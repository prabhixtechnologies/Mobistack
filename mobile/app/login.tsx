import { useEffect, useState } from "react";
import { Linking, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useAuth } from "../lib/auth";
import { api, selectedWorkspaceId, type AuthResponse, type AuthUser } from "../lib/api";
import { BRAND, copyrightLine } from "../lib/brand";
import { getDeviceId } from "../lib/device";
import { useTheme } from "../lib/theme";

type Method = "password" | "magic" | "email" | "phone" | "whatsapp" | "sso" | "register" | "shop" | "forgot";

const METHODS: { id: Method; label: string }[] = [
  { id: "password", label: "Password" },
  { id: "magic", label: "Magic" },
  { id: "email", label: "Email code" },
  { id: "phone", label: "Phone" },
  { id: "whatsapp", label: "WhatsApp" },
  { id: "sso", label: "SSO" },
  { id: "register", label: "New user" },
  { id: "shop", label: "Open a shop" },
  { id: "forgot", label: "Forgot" },
];

export default function LoginScreen() {
  const { login, acceptSession, registerShop } = useAuth();
  const { colors, toggle, mode } = useTheme();
  const params = useLocalSearchParams<{ code?: string; sso?: string }>();
  const [method, setMethod] = useState<Method>("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [shopName, setShopName] = useState("");
  const [city, setCity] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function go(user: AuthUser) {
    router.replace(selectedWorkspaceId(user) ? "/(tabs)/home" : "/workspaces");
  }

  async function onContinue() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (method === "password") {
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
      } else if (method === "sso") {
        setError("Use Continue with Google to sign in with your company account.");
      } else if (method === "forgot") {
        await api("/api/v1/auth/forgot-password", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice("If that email is registered, a reset link was sent.");
      } else if (method === "shop") {
        await go(await registerShop({
          shopName: shopName.trim(),
          ownerName: fullName.trim(),
          email: email.trim(),
          password,
          phone: phone.trim() || undefined,
          city: city.trim() || undefined,
        }));
      } else {
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

  useEffect(() => {
    const code = typeof params.code === "string" ? params.code : undefined;
    if (params.sso !== "google" || !code) {
      return;
    }
    setBusy(true);
    void getDeviceId().then((deviceId) =>
      api<AuthResponse>("/api/v1/auth/sso/google", {
      method: "POST",
      body: JSON.stringify({
        code,
        redirectUri: "mobistack://login?sso=google",
        deviceId,
      }),
    }))
      .then(async (auth) => {
        await go(await acceptSession(auth));
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Google sign-in failed");
        setMethod("sso");
      })
      .finally(() => setBusy(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.code, params.sso]);

  const styles = makeStyles(colors);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.wrap}>
      <View style={styles.top}>
        <Text style={styles.mark}>{BRAND.product}</Text>
        <Pressable onPress={toggle}>
          <Text style={styles.ghostText}>{mode === "light" ? "Dark" : "Light"}</Text>
        </Pressable>
      </View>
      <Text style={styles.title}>Open the counter.</Text>
      <Text style={styles.sub}>{BRAND.tagline}</Text>
      <View style={styles.chips}>
        {METHODS.map((item) => (
          <Pressable
            key={item.id}
            style={[styles.chip, method === item.id && styles.chipOn]}
            onPress={() => {
              setMethod(item.id);
              setError(null);
              setNotice(null);
            }}
          >
            <Text style={method === item.id ? styles.chipOnText : styles.chipText}>{item.label}</Text>
          </Pressable>
        ))}
      </View>
      {(method === "password" ||
        method === "magic" ||
        method === "email" ||
        method === "sso" ||
        method === "register" ||
        method === "shop" ||
        method === "forgot") && (
        <TextInput
          style={styles.input}
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          placeholder="you@prabhixtechnologies.com"
          placeholderTextColor={colors.faint}
        />
      )}
      {(method === "password" || method === "register" || method === "shop") && (
        <TextInput
          style={styles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder="Password"
          placeholderTextColor={colors.faint}
        />
      )}
      {(method === "sso" || method === "register" || method === "shop") && (
        <TextInput
          style={styles.input}
          value={fullName}
          onChangeText={setFullName}
          placeholder={method === "shop" ? "Owner name" : "Full name"}
          placeholderTextColor={colors.faint}
        />
      )}
      {method === "shop" && (
        <>
          <TextInput
            style={styles.input}
            value={shopName}
            onChangeText={setShopName}
            placeholder="Shop name"
            placeholderTextColor={colors.faint}
          />
          <TextInput
            style={styles.input}
            value={city}
            onChangeText={setCity}
            placeholder="City"
            placeholderTextColor={colors.faint}
          />
        </>
      )}
      {(method === "phone" || method === "whatsapp" || method === "register" || method === "shop") && (
        <TextInput
          style={styles.input}
          value={phone}
          onChangeText={setPhone}
          keyboardType="phone-pad"
          placeholder={method === "whatsapp" ? "WhatsApp number" : "Mobile number"}
          placeholderTextColor={colors.faint}
        />
      )}
      {(method === "email" || method === "phone" || method === "whatsapp") && (
        <TextInput
          style={styles.input}
          value={code}
          onChangeText={setCode}
          keyboardType="number-pad"
          placeholder="Code (blank to request)"
          placeholderTextColor={colors.faint}
        />
      )}
      {notice ? <Text style={styles.sub}>{notice}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {method === "sso" && (
        <Pressable
          style={styles.btn}
          onPress={async () => {
            setError(null);
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
          }}
        >
          <Text style={styles.btnText}>Continue with Google</Text>
        </Pressable>
      )}
      <Pressable style={styles.btn} onPress={() => void onContinue()}>
        <Text style={styles.btnText}>
          {busy ? "Working…" : method === "forgot" ? "Send reset link" : method === "shop" ? "Create shop" : "Continue"}
        </Text>
      </Pressable>
      <Text style={styles.legal}>{copyrightLine()}</Text>
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    wrap: { padding: 28, paddingTop: 72, paddingBottom: 48 },
    top: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 18 },
    mark: { fontSize: 18, fontWeight: "700", color: colors.ink },
    title: { fontSize: 40, lineHeight: 44, fontWeight: "500", letterSpacing: -1, marginBottom: 8, color: colors.ink },
    sub: { color: colors.soft, fontSize: 16, marginBottom: 22 },
    chips: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 16 },
    chip: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 999,
      paddingHorizontal: 10,
      paddingVertical: 6,
    },
    chipOn: { backgroundColor: colors.ink, borderColor: colors.ink },
    chipText: { fontWeight: "600", color: colors.soft, fontSize: 13 },
    chipOnText: { fontWeight: "600", color: colors.bg, fontSize: 13 },
    input: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 14,
      marginBottom: 12,
      color: colors.ink,
    },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 16, alignItems: "center", marginTop: 8 },
    btnText: { color: colors.bg, fontWeight: "700" },
    ghostText: { fontWeight: "700", color: colors.soft },
    error: { color: colors.bad, marginBottom: 8 },
    legal: { color: colors.faint, fontSize: 12, marginTop: 28, lineHeight: 18 },
  });
}
