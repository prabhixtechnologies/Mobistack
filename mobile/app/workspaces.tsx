import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { useAuth } from "../lib/auth";
import { selectedWorkspaceId, type WorkspaceCard } from "../lib/api";
import { useTheme } from "../lib/theme";

export default function WorkspacesScreen() {
  const { workspaces, refreshWorkspaces, switchWorkspace, createWorkspace, joinWorkspace, user } = useAuth();
  const { colors } = useTheme();
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const styles = makeStyles(colors);

  useEffect(() => {
    refreshWorkspaces().catch((err: Error) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function openWorkspace(id: string) {
    setBusy(true);
    setError(null);
    try {
      await switchWorkspace(id);
      router.replace("/(tabs)/home");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not switch");
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView style={styles.page} contentContainerStyle={styles.content}>
      <Text style={styles.title}>My workspaces</Text>
      <Text style={styles.sub}>One account. Many shops.</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {notice ? <Text style={styles.notice}>{notice}</Text> : null}

      {workspaces.map((workspace) => (
        <WorkspaceRow
          key={workspace.id}
          workspace={workspace}
          busy={busy}
          colors={colors}
          onOpen={() => void openWorkspace(workspace.id)}
        />
      ))}

      <Text style={styles.section}>Create</Text>
      <TextInput style={styles.input} placeholder="Workspace name" placeholderTextColor={colors.faint} value={name} onChangeText={setName} />
      <TextInput style={styles.input} placeholder="City" placeholderTextColor={colors.faint} value={city} onChangeText={setCity} />
      <Pressable
        style={styles.btn}
        disabled={busy || !name.trim()}
        onPress={async () => {
          setBusy(true);
          setError(null);
          try {
            await createWorkspace(name.trim(), city.trim() || undefined);
            router.replace("/(tabs)/home");
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not create");
          } finally {
            setBusy(false);
          }
        }}
      >
        <Text style={styles.btnText}>Create and open</Text>
      </Pressable>

      <Text style={styles.section}>Join with a code</Text>
      <TextInput
        style={styles.input}
        placeholder="HUB-7K2P"
        autoCapitalize="characters"
        placeholderTextColor={colors.faint}
        value={joinCode}
        onChangeText={setJoinCode}
      />
      <Pressable
        style={styles.ghost}
        disabled={busy || !joinCode.trim()}
        onPress={async () => {
          setBusy(true);
          setError(null);
          try {
            const card = await joinWorkspace(joinCode.trim());
            setJoinCode("");
            setNotice(
              card.status === "PENDING"
                ? `Asked to join ${card.name}. Waiting for approval.`
                : `Joined ${card.name}.`,
            );
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not join");
          } finally {
            setBusy(false);
          }
        }}
      >
        <Text style={styles.ghostText}>Request access</Text>
      </Pressable>

      {selectedWorkspaceId(user) ? (
        <Pressable style={styles.ghost} onPress={() => router.back()}>
          <Text style={styles.ghostText}>Back</Text>
        </Pressable>
      ) : null}
    </ScrollView>
  );
}

function WorkspaceRow({
  workspace,
  busy,
  onOpen,
  colors,
}: {
  workspace: WorkspaceCard;
  busy: boolean;
  onOpen: () => void;
  colors: ReturnType<typeof useTheme>["colors"];
}) {
  const canOpen = workspace.status === "ACTIVE" && !workspace.selected;
  return (
    <View style={{ backgroundColor: colors.card, borderColor: colors.line, borderWidth: 1, borderRadius: 16, padding: 16, marginBottom: 10 }}>
      <Text style={{ fontWeight: "700", fontSize: 18, color: colors.ink }}>{workspace.name}</Text>
      <Text style={{ color: colors.soft, marginTop: 4 }}>
        {[workspace.city, workspace.role, workspace.status].filter(Boolean).join(" · ")}
      </Text>
      {workspace.joinCode ? <Text style={{ color: colors.soft, marginTop: 4 }}>Code {workspace.joinCode}</Text> : null}
      {canOpen ? (
        <Pressable
          style={{ backgroundColor: colors.ink, borderRadius: 12, padding: 10, alignItems: "center", marginTop: 12, alignSelf: "flex-start" }}
          disabled={busy}
          onPress={onOpen}
        >
          <Text style={{ color: colors.bg, fontWeight: "700" }}>Open</Text>
        </Pressable>
      ) : (
        <Text style={{ color: colors.soft, marginTop: 4 }}>{workspace.selected ? "Selected" : "Waiting for approval"}</Text>
      )}
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg },
    content: { padding: 22, paddingTop: 72, paddingBottom: 48 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    sub: { color: colors.soft, marginTop: 6, marginBottom: 18 },
    section: { marginTop: 22, marginBottom: 10, fontWeight: "700", color: colors.ink },
    error: { color: colors.bad, marginBottom: 10 },
    notice: { color: colors.good, marginBottom: 10 },
    input: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 14,
      marginBottom: 10,
      color: colors.ink,
    },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center" },
    btnText: { color: colors.bg, fontWeight: "700" },
    ghost: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 14,
      alignItems: "center",
      marginTop: 8,
    },
    ghostText: { fontWeight: "700", color: colors.ink },
  });
}
