import { useEffect, useState } from "react";
import { Linking, Pressable, StyleSheet, Text, View } from "react-native";
import { router } from "expo-router";
import { useAuth } from "../../lib/auth";
import { selectedWorkspaceId } from "../../lib/api";
import { copyrightLine, BRAND } from "../../lib/brand";
import { lastPulledAt, syncNow } from "../../lib/offline";
import { pendingCount } from "../../lib/outbox";
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

  useEffect(() => {
    void pendingCount().then(setPending);
    void lastPulledAt().then(setPulled);
  }, []);

  return (
    <View style={styles.page}>
      <Text style={styles.title}>More</Text>
      <Text style={styles.name}>{user?.fullName}</Text>
      <Text style={styles.sub}>
        {user?.workspaceName ?? user?.shopName} · {user?.roles[0]}
      </Text>
      <Text style={styles.legal}>{BRAND.tagline}</Text>
      <Text style={styles.legal}>{copyrightLine()}</Text>
      {user?.paymentRequired ? (
        <Pressable style={styles.ghost} onPress={() => void Linking.openURL(`${BRAND.publicOrigin}/billing`)}>
          <Text style={styles.ghostText}>Pay on the web console</Text>
        </Pressable>
      ) : null}
      {user?.catalogOnly ? (
        <Pressable style={styles.ghost} onPress={() => void Linking.openURL(`${BRAND.publicOrigin}/billing`)}>
          <Text style={styles.ghostText}>Upgrade to the full shop</Text>
        </Pressable>
      ) : null}

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

      <Pressable style={styles.ghost} onPress={() => router.push("/workspaces")}>
        <Text style={styles.ghostText}>My workspaces</Text>
      </Pressable>
      <Pressable style={styles.ghost} onPress={() => router.push("/inbox")}>
        <Text style={styles.ghostText}>Notifications</Text>
      </Pressable>
      <Pressable style={styles.ghost} onPress={() => router.push("/support")}>
        <Text style={styles.ghostText}>Contact support</Text>
      </Pressable>
      <Pressable style={styles.ghost} onPress={() => router.push("/customers")}>
        <Text style={styles.ghostText}>Customers</Text>
      </Pressable>
      <Pressable
        style={styles.ghost}
        onPress={async () => {
          const result = await syncNow();
          setPending(result.pending);
          setPulled(result.pulledAt);
          setNotice(result.pending === 0 ? "Device caught up." : `${result.pending} items still waiting.`);
        }}
      >
        <Text style={styles.ghostText}>
          Sync now{pending ? ` · ${pending} queued` : ""}
        </Text>
      </Pressable>
      <Pressable style={styles.ghost} onPress={toggle}>
        <Text style={styles.ghostText}>{mode === "light" ? "Switch to dark mode" : "Switch to light mode"}</Text>
      </Pressable>
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
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg, padding: 22, paddingTop: 72 },
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
