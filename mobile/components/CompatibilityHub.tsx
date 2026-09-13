import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { api } from "../lib/api";
import {
  groupLine,
  type CompatibilityGroup,
  type CompatibilityOverview,
} from "../lib/compatibility";
import { useTheme } from "../lib/theme";

interface DeviceHit {
  id: string;
  name: string;
  brandName: string;
  variant?: string;
}

export function CompatibilityHub({ greeting }: { greeting?: string }) {
  const { colors } = useTheme();
  const [query, setQuery] = useState("");
  const [overview, setOverview] = useState<CompatibilityOverview | null>(null);
  const [groups, setGroups] = useState<CompatibilityGroup[]>([]);
  const [devices, setDevices] = useState<DeviceHit[]>([]);
  const [error, setError] = useState<string | null>(null);
  const styles = makeStyles(colors);

  useEffect(() => {
    api<CompatibilityOverview>("/api/v1/compatibility-groups/overview")
      .then(setOverview)
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setGroups([]);
      setDevices([]);
      return;
    }
    const handle = setTimeout(() => {
      Promise.all([
        api<{ content: CompatibilityGroup[] }>(
          `/api/v1/compatibility-groups?q=${encodeURIComponent(query.trim())}&size=40`,
        ),
        api<{ devices: DeviceHit[] }>(`/api/v1/search?q=${encodeURIComponent(query.trim())}`),
      ])
        .then(([page, search]) => {
          setGroups(page.content ?? []);
          setDevices(search.devices ?? []);
          setError(null);
        })
        .catch((err: Error) => setError(err.message));
    }, 150);
    return () => clearTimeout(handle);
  }, [query]);

  const searching = query.trim().length >= 2;

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      {greeting ? <Text style={styles.hello}>{greeting}</Text> : null}
      <Text style={styles.title}>Compatibility</Text>
      <Text style={styles.sub}>Open a part list. Phones that share that part sit on one line.</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput
        style={styles.search}
        placeholder="Search 9A, Realme 6…"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Search compatibility lists"
        value={query}
        onChangeText={setQuery}
      />
      {searching ? (
        <>
          <Text style={styles.match}>Match: {groups.length}</Text>
          {groups.map((group, index) => (
            <Pressable
              key={group.id}
              style={styles.row}
              onPress={() => group.categoryId && router.push(`/compatibility/${group.categoryId}`)}
            >
              <Text style={styles.n}>{index + 1}</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.meta}>{group.categoryName ?? "Unfiled"}</Text>
                <Highlighted text={groupLine(group.devices) || group.name} query={query} colors={colors} />
              </View>
            </Pressable>
          ))}
          {groups.length === 0 && devices.length === 0 ? <Text style={styles.meta}>No matching group or phone yet.</Text> : null}
          {devices.map((device) => (
            <Pressable key={device.id} style={styles.row} onPress={() => router.push(`/device/${device.id}`)}>
              <View style={{ flex: 1 }}>
                <Text style={styles.cardTitle}>
                  {[device.brandName, device.name, device.variant].filter(Boolean).join(" ")}
                </Text>
                <Text style={styles.meta}>Stock and price</Text>
              </View>
            </Pressable>
          ))}
        </>
      ) : (
        (overview?.categories ?? []).map((category, index) => (
          <Pressable
            key={category.id}
            style={styles.card}
            onPress={() => router.push(`/compatibility/${category.id}`)}
          >
            <View style={[styles.badge, { backgroundColor: category.color || colors.accent }]}>
              <Text style={styles.badgeText}>{index + 1}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardTitle}>{category.name}</Text>
              <Text style={styles.meta}>
                {category.groupCount} {category.groupCount === 1 ? "group" : "groups"}
              </Text>
            </View>
          </Pressable>
        ))
      )}
    </ScrollView>
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
  const idx = needle ? text.toLowerCase().indexOf(needle.toLowerCase()) : -1;
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
    hello: { fontSize: 28, fontWeight: "500", letterSpacing: -0.6, color: colors.ink, marginBottom: 8 },
    title: { fontSize: 32, fontWeight: "700", letterSpacing: -0.8, color: colors.ink },
    sub: { color: colors.soft, marginTop: 8, marginBottom: 16, fontWeight: "600" },
    error: { color: colors.bad, marginBottom: 12, fontWeight: "600" },
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 18,
      padding: 14,
      marginBottom: 14,
      color: colors.ink,
    },
    match: { color: colors.good, fontWeight: "700", marginBottom: 10 },
    card: {
      backgroundColor: colors.card,
      borderRadius: 18,
      padding: 16,
      marginBottom: 10,
      flexDirection: "row",
      gap: 12,
      alignItems: "center",
      borderWidth: 1,
      borderColor: colors.line,
    },
    badge: { width: 32, height: 32, borderRadius: 10, alignItems: "center", justifyContent: "center" },
    badgeText: { color: "#fff", fontWeight: "800" },
    cardTitle: { fontWeight: "800", color: colors.ink, fontSize: 16 },
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
  });
}
