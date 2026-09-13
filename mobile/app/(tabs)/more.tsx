import { useEffect, useState } from "react";
import { Alert, Image, Linking, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import * as Application from "expo-application";
import { router } from "expo-router";
import { useAuth } from "../../lib/auth";
import { selectedWorkspaceId } from "../../lib/api";
import { copyrightLine, BRAND } from "../../lib/brand";
import { lastPulledAt, syncNow } from "../../lib/offline";
import { discard, pendingCount, type FailedOp } from "../../lib/outbox";
import { hasFeature, hasPermission } from "../../lib/plan";
import { registerForPush } from "../../lib/push";
import { useTheme } from "../../lib/theme";

export default function MoreScreen() {
  const { user, workspaces, logout, switchWorkspace } = useAuth();
  const { colors, toggle, mode } = useTheme();
  const current = selectedWorkspaceId(user);
  const active = workspaces.filter((workspace) => workspace.status === "ACTIVE");
  const [pending, setPending] = useState(0);
  const [pulled, setPulled] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [stuck, setStuck] = useState<FailedOp | null>(null);
  const styles = makeStyles(colors);
  const version = `${Constants.expoConfig?.version ?? "1.2.0"} (${Application.nativeBuildVersion ?? "5"})`;
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
                  // A switch can legitimately refuse while offline work is
                  // queued, so the reason has to reach the screen.
                  switchWorkspace(workspace.id).catch((err: unknown) => {
                    setNotice(err instanceof Error ? err.message : "Could not switch shops.");
                  });
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
      {show("COMPATIBILITY", "CATALOG_READ") || user?.catalogOnly
        ? row("Compatibility lists", () => router.push("/compatibility"))
        : null}
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
      {row("Check push notifications", () => {
        void registerForPush().then((outcome) => {
          if (outcome.state === "registered") {
            setNotice("This phone is set up for alerts.");
          } else if (outcome.state === "denied") {
            setNotice("Notifications are turned off for MobiStack. Turn them on in your phone's Settings app.");
          } else {
            setNotice(`Alerts are not available on this phone: ${outcome.reason}`);
          }
        });
      })}
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
            setStuck(result.failed[0] ?? null);
            if (result.blocked) {
              // The server refused the whole batch — a lapsed plan, usually.
              // Calling that "no connection" sends the shopkeeper to check
              // their wifi instead of their subscription.
              setNotice(result.blocked);
            } else if (result.failed.length > 0) {
              // Say which operation is stuck. A rejected item retries forever
              // otherwise, and the queued count alone explains nothing.
              const first = result.failed[0];
              setNotice(`${first.type} could not sync: ${first.message}`);
            } else if (result.offline) {
              setNotice("No connection. Your changes are saved and will sync later.");
            } else {
              setNotice(result.pending === 0 ? "Device caught up." : `${result.pending} items still waiting.`);
            }
          });
        },
      )}
      {stuck
        // A rejected operation cannot be fixed from here and blocks the queue,
        // which in turn blocks switching shops. Dropping it has to be possible,
        // but only after the shopkeeper has seen what is being thrown away.
        ? row(`Discard the stuck ${stuck.type.toLowerCase()}`, () => {
          Alert.alert(
            "Discard this change?",
            `${stuck.type} could not sync: ${stuck.message}\n\nIt will be removed from this phone and never reach the server.`,
            [
              { text: "Keep trying", style: "cancel" },
              {
                text: "Discard",
                style: "destructive",
                onPress: () => {
                  void discard(stuck.idempotencyKey).then(async () => {
                    setStuck(null);
                    setPending(await pendingCount());
                    setNotice("Change discarded.");
                  });
                },
              },
            ],
          );
        })
        : null}
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
    title: { fontSize: 32, fontWeight: "600", color: colors.ink },
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
    chipOn: { backgroundColor: colors.accent, borderColor: colors.accent },
    chipText: { fontWeight: "600", color: colors.ink },
    chipOnText: { fontWeight: "600", color: colors.accentInk },
    ghost: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 14,
      alignItems: "center",
      marginBottom: 10,
    },
    ghostText: { fontWeight: "700", color: colors.ink },
    btn: { backgroundColor: colors.accent, borderRadius: 14, padding: 14, alignItems: "center", marginTop: 8 },
    btnText: { color: colors.accentInk, fontWeight: "700" },
  });
}
