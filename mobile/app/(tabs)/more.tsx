import { useEffect, useState } from "react";
import { Image, Linking, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import * as Application from "expo-application";
import { router } from "expo-router";
import { useAuth } from "../../lib/auth";
import { selectedWorkspaceId } from "../../lib/api";
import { copyrightLine, BRAND } from "../../lib/brand";
import { lastPulledAt, syncNow } from "../../lib/offline";
import { pendingCount } from "../../lib/outbox";
import { hasFeature, hasPermission } from "../../lib/plan";
import { useTheme } from "../../lib/theme";

export default function MoreScreen() {
  const { user, workspaces, logout, switchWorkspace } = useAuth();
  const { colors, toggle, mode } = useTheme();
  const current = selectedWorkspaceId(user);
  const active = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const [pending, setPending] = useState(0);
  const [pulled, setPulled] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const styles = makeStyles(colors);
  const version = `${Constants.expoConfig?.version ?? "1.1.0"} (${Application.nativeBuildVersion ?? "2"})`;
  const openShop = !user?.catalogOnly && !user?.paymentRequired && (user?.features?.length ?? 0) === 0;
  const show = (feature: string, permission: string) =>
    hasFeature(user, feature) || hasPermission(user, permission) || openShop;

  useEffect(() => {
    void pendingCount().then(setPending);
    void lastPulledAt().then(setPulled);
  }, []);

  function row(label: string, onPress: () => void) {
    return (
      <Pressable style={styles.ghost} onPress={onPress}>
        <Text style={styles.ghostText}>{label}</Text>
      </Pressable>
    );
  }

  return (
    <ScrollView style={styles.page} contentContainerStyle={{ paddingBottom: 48 }}>
      <Image source={require("../../assets/logo.png")} style={styles.logo} />
      <Text style={styles.title}>More</Text>
      <Text style={styles.name}>{user?.fullName}</Text>
      <Text style={styles.sub}>
        {user?.workspaceName ?? user?.shopName} · {user?.roles[0]}
      </Text>
      <Text style={styles.legal}>{BRAND.tagline}</Text>
      <Text style={styles.legal}>{copyrightLine()}</Text>
      <Text style={styles.legal}>MobiStack {version}</Text>
      {user?.paymentRequired ? row("Pay to activate this shop", () => router.push("/billing")) : null}
      {user?.catalogOnly ? row("Upgrade to the full shop", () => router.push("/billing")) : null}

      {active.length > 1 && (
        <View style={styles.block}>
          <Text style={styles.label}>Workspace</Text>
          {active.map((workspace) => (
            <Pressable
              key={workspace.id}
              style={[styles.chip, workspace.id === current && styles.chipOn]}
              onPress={() => {
                if (workspace.id !== current) {
                  void switchWorkspace(workspace.id);
                }
              }}
            >
              <Text style={workspace.id === current ? styles.chipOnText : styles.chipText}>{workspace.name}</Text>
            </Pressable>
          ))}
        </View>
      )}

      {row("My profile", () => router.push("/profile"))}
      {row("My workspaces", () => router.push("/workspaces"))}
      {show("CUSTOMERS", "CUSTOMER_READ") ? row("Customers", () => router.push("/customers")) : null}
      {show("SUPPLIERS", "SUPPLIER_READ") ? row("Suppliers", () => router.push("/suppliers")) : null}
      {show("PURCHASES", "PURCHASE_READ") ? row("Purchases", () => router.push("/purchases")) : null}
      {show("MEMBERS", "USER_READ") ? row("Members", () => router.push("/members")) : null}
      {show("REPORTS", "REPORT_READ") ? row("Reports", () => router.push("/reports")) : null}
      {show("MOVEMENTS", "INVENTORY_READ") ? row("Movements", () => router.push("/movements")) : null}
      {hasPermission(user, "WORKSPACE_BILLING") || user?.paymentRequired
        ? row("Billing", () => router.push("/billing"))
        : null}
      {hasPermission(user, "SETTINGS_READ") ? row("Shop settings", () => router.push("/settings")) : null}
      {row("Notifications", () => router.push("/inbox"))}
      {row("Contact support", () => router.push("/support"))}
      {hasFeature(user, "IMPORT") || hasPermission(user, "CATALOG_WRITE")
        ? row("Import catalogue (web)", () => void Linking.openURL(`${BRAND.publicOrigin}/import`))
        : null}
      {hasFeature(user, "AUDIT") || hasPermission(user, "AUDIT_READ")
        ? row("Audit log (web)", () => void Linking.openURL(`${BRAND.publicOrigin}/audit`))
        : null}
      {hasPermission(user, "USER_READ")
        ? row("Access control (web)", () => void Linking.openURL(`${BRAND.publicOrigin}/users`))
        : null}
      {user?.systemAdmin ? row("Platform console (web)", () => void Linking.openURL(`${BRAND.publicOrigin}/admin`)) : null}
      {row("Open the web console", () => void Linking.openURL(BRAND.publicOrigin))}
      {row(
        `Sync now${pending ? ` · ${pending} queued` : ""}`,
        () => {
          void syncNow().then((result) => {
            setPending(result.pending);
            setPulled(result.pulledAt);
            setNotice(result.pending === 0 ? "Device caught up." : `${result.pending} items still waiting.`);
          });
        },
      )}
      {row(mode === "light" ? "Switch to dark mode" : "Switch to light mode", toggle)}
      {notice ? <Text style={styles.sub}>{notice}</Text> : null}
      {pulled ? <Text style={styles.legal}>Last snapshot {pulled}</Text> : null}
      <Pressable
        style={styles.btn}
        onPress={async () => {
          await logout();
          router.replace("/login");
        }}
      >
        <Text style={styles.btnText}>Sign out</Text>
      </Pressable>
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg, padding: 22, paddingTop: 72 },
    logo: { width: 40, height: 40, borderRadius: 12, marginBottom: 14 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    name: { marginTop: 18, fontSize: 20, fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 6, marginBottom: 12 },
    legal: { color: colors.faint, fontSize: 12, marginBottom: 4 },
    block: { marginBottom: 18, marginTop: 16 },
    label: { color: colors.faint, marginBottom: 8, fontWeight: "600" },
    chip: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 12,
      padding: 12,
      marginBottom: 8,
      backgroundColor: colors.card,
    },
    chipOn: { backgroundColor: colors.ink, borderColor: colors.ink },
    chipText: { fontWeight: "600", color: colors.ink },
    chipOnText: { fontWeight: "600", color: colors.bg },
    ghost: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 14,
      alignItems: "center",
      marginBottom: 10,
    },
    ghostText: { fontWeight: "700", color: colors.ink },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center", marginTop: 8 },
    btnText: { color: colors.bg, fontWeight: "700" },
  });
}
