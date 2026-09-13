import { useLocalSearchParams, router } from "expo-router";
import { useEffect, useState } from "react";
import {
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import {
  groupLine,
  splitEqualsLine,
  type CategoryOverview,
  type CompatibilityGroup,
  type CompatibilityOverview,
} from "../../lib/compatibility";
import { hasPermission } from "../../lib/plan";
import { useTheme } from "../../lib/theme";

export default function CompatibilityCategoryScreen() {
  const { categoryId } = useLocalSearchParams<{ categoryId: string }>();
  const { user } = useAuth();
  const canWrite = hasPermission(user, "CATALOG_WRITE");
  const { colors } = useTheme();
  const [category, setCategory] = useState<CategoryOverview | null>(null);
  const [groups, setGroups] = useState<CompatibilityGroup[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [editor, setEditor] = useState<{ group: CompatibilityGroup | null; name: string; line: string } | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const styles = makeStyles(colors);

  async function load(nextQuery = query) {
    if (!categoryId) {
      return;
    }
    const params = new URLSearchParams({ categoryId, size: "200" });
    if (nextQuery.trim().length >= 2) {
      params.set("q", nextQuery.trim());
    }
    const [overview, page] = await Promise.all([
      api<CompatibilityOverview>("/api/v1/compatibility-groups/overview"),
      api<{ content: CompatibilityGroup[] }>(`/api/v1/compatibility-groups?${params}`),
    ]);
    setCategory(overview.categories.find((row) => row.id === categoryId) ?? null);
    setGroups(page.content ?? []);
  }

  useEffect(() => {
    const handle = setTimeout(() => {
      load(query).catch((err: Error) => setError(err.message));
    }, query.trim().length < 2 ? 0 : 150);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, query]);

  async function save() {
    if (!editor || !categoryId) {
      return;
    }
    const names = splitEqualsLine(editor.line);
    if (names.length === 0) {
      setError("Add at least one phone.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const name = editor.name.trim() || names[0];
      if (editor.group) {
        await api(`/api/v1/compatibility-groups/${editor.group.id}`, {
          method: "PUT",
          body: JSON.stringify({ name, categoryId, verified: editor.group.verified, active: true }),
        });
        await api(`/api/v1/compatibility-groups/${editor.group.id}/membership`, {
          method: "PUT",
          body: JSON.stringify({ deviceTexts: names }),
        });
      } else {
        await api("/api/v1/compatibility-groups", {
          method: "POST",
          body: JSON.stringify({ name, categoryId, verified: false, active: true, deviceTexts: names }),
        });
      }
      setEditor(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    } finally {
      setBusy(false);
    }
  }

  function copyGroup(group: CompatibilityGroup) {
    void (async () => {
      try {
        await api(`/api/v1/compatibility-groups/${group.id}/copy`, {
          method: "POST",
          body: JSON.stringify({}),
        });
        await load();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not copy");
      }
    })();
  }

  function removeGroup(group: CompatibilityGroup) {
    Alert.alert("Delete this group?", "Phones on this line will no longer share this part.", [
      { text: "Cancel", style: "cancel" },
      {
        text: "Delete",
        style: "destructive",
        onPress: () => {
          void (async () => {
            try {
              await api(`/api/v1/compatibility-groups/${group.id}`, { method: "DELETE" });
              await load();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Could not delete");
            }
          })();
        },
      },
    ]);
  }

  const match = query.trim().length >= 2;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={styles.page}>
        <Pressable onPress={() => router.back()} style={{ marginBottom: 8 }}>
          <Text style={{ color: colors.soft, fontWeight: "700" }}>All lists</Text>
        </Pressable>
        <Text style={styles.title}>{category?.name ?? "List"}</Text>
        <Text style={styles.sub}>
          {match ? `Match: ${groups.length}` : `${groups.length} groups. Same part, one line.`}
        </Text>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <TextInput
          style={styles.search}
          placeholder="Find a model…"
          placeholderTextColor={colors.faint}
          accessibilityLabel="Find a model"
          value={query}
          onChangeText={setQuery}
        />
        {canWrite ? (
          <Pressable style={styles.btn} onPress={() => setEditor({ group: null, name: "", line: "" })}>
            <Text style={styles.btnText}>Add group</Text>
          </Pressable>
        ) : null}
        {groups.map((group, index) => {
          const line = groupLine(group.devices) || group.name;
          const hit = match && line.toLowerCase().includes(query.trim().toLowerCase());
          return (
            <View key={group.id} style={[styles.row, hit && { backgroundColor: colors.warnSoft }]}>
              <Text style={styles.n}>{index + 1}</Text>
              <View style={{ flex: 1 }}>
                <Highlighted text={line} query={query} colors={colors} />
                <Text style={styles.meta}>
                  {group.name}
                  {group.verified ? "  ✓" : ""}
                </Text>
                {canWrite ? (
                  <View style={styles.actions}>
                    <Pressable onPress={() => setEditor({ group, name: group.name, line })}>
                      <Text style={styles.link}>Edit</Text>
                    </Pressable>
                    <Pressable onPress={() => copyGroup(group)}>
                      <Text style={styles.link}>Copy</Text>
                    </Pressable>
                    <Pressable onPress={() => removeGroup(group)}>
                      <Text style={[styles.link, { color: colors.bad }]}>Delete</Text>
                    </Pressable>
                  </View>
                ) : null}
              </View>
            </View>
          );
        })}
        {groups.length === 0 ? <Text style={styles.meta}>No groups in this list yet.</Text> : null}
      </ScrollView>

      <Modal visible={editor != null} animationType="slide" transparent>
        <View style={styles.sheetScrim}>
          <View style={[styles.sheet, { backgroundColor: colors.card }]}>
            <Text style={[styles.sheetTitle, { color: colors.ink }]}>
              {editor?.group ? "Edit group" : "Add group"}
            </Text>
            <TextInput
              style={styles.search}
              placeholder="Name (optional)"
              placeholderTextColor={colors.faint}
              accessibilityLabel="List name"
              value={editor?.name ?? ""}
              onChangeText={(name) => editor && setEditor({ ...editor, name })}
            />
            <TextInput
              style={[styles.search, { minHeight: 120, textAlignVertical: "top" }]}
              placeholder="Samsung A32 4G = Samsung M32 4G"
              placeholderTextColor={colors.faint}
              accessibilityLabel="Compatibility lines"
              multiline
              value={editor?.line ?? ""}
              onChangeText={(line) => editor && setEditor({ ...editor, line })}
            />
            <View style={{ flexDirection: "row", gap: 10 }}>
              <Pressable style={[styles.btn, { flex: 1, backgroundColor: colors.line }]} onPress={() => setEditor(null)}>
                <Text style={[styles.btnText, { color: colors.ink }]}>Cancel</Text>
              </Pressable>
              <Pressable style={[styles.btn, { flex: 1 }]} onPress={() => void save()} disabled={busy}>
                <Text style={styles.btnText}>{busy ? "Saving…" : "Save"}</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

function Highlighted({
  text,
  query,
  colors,
}: {
  text: string;
  query: string;
  colors: ReturnType<typeof useTheme>["colors"];
}) {
  const needle = query.trim();
  const idx = needle.length >= 2 ? text.toLowerCase().indexOf(needle.toLowerCase()) : -1;
  if (idx < 0) {
    return <Text style={{ fontWeight: "700", color: colors.ink, lineHeight: 22 }}>{text}</Text>;
  }
  return (
    <Text style={{ fontWeight: "700", color: colors.ink, lineHeight: 22 }}>
      {text.slice(0, idx)}
      <Text style={{ backgroundColor: colors.badSoft, color: colors.bad }}>
        {text.slice(idx, idx + needle.length)}
      </Text>
      {text.slice(idx + needle.length)}
    </Text>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 48 },
    title: { fontSize: 30, fontWeight: "700", letterSpacing: -0.7, color: colors.ink },
    sub: { color: colors.soft, marginTop: 8, marginBottom: 14, fontWeight: "600" },
    error: { color: colors.bad, marginBottom: 12, fontWeight: "600" },
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 16,
      padding: 14,
      marginBottom: 12,
      color: colors.ink,
    },
    btn: {
      backgroundColor: colors.accent,
      borderRadius: 14,
      padding: 14,
      alignItems: "center",
      marginBottom: 14,
    },
    btnText: { color: colors.accentInk, fontWeight: "700" },
    row: {
      backgroundColor: colors.card,
      borderRadius: 16,
      padding: 14,
      marginBottom: 8,
      flexDirection: "row",
      gap: 10,
    },
    n: { fontWeight: "800", color: colors.accent, width: 22 },
    meta: { color: colors.soft, marginTop: 4, fontWeight: "600" },
    actions: { flexDirection: "row", gap: 16, marginTop: 10 },
    link: { fontWeight: "700", color: colors.accent },
    sheetScrim: { flex: 1, backgroundColor: "rgba(0,0,0,0.35)", justifyContent: "flex-end" },
    sheet: { padding: 20, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
    sheetTitle: { fontSize: 22, fontWeight: "700", marginBottom: 12 },
  });
}
