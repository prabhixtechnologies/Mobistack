import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import { router } from "expo-router";
import { api } from "../lib/api";
import { cachedInbox, saveInbox } from "../lib/offline";
import { clearBadge, listenForPush } from "../lib/push";
import { useTheme } from "../lib/theme";

interface Item {
  id: string;
  title: string;
  body?: string;
  link?: string | null;
  readAt?: string | null;
}

interface Inbox {
  unread: number;
  items: Item[];
}

const EMPTY: Inbox = { unread: 0, items: [] };

export default function InboxScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [inbox, setInbox] = useState<Inbox>(EMPTY);
  const [offline, setOffline] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const fresh = await api<Inbox>("/api/v1/inbox");
      setInbox(fresh);
      setOffline(false);
      await saveInbox(fresh);
      if (fresh.unread === 0) {
        await clearBadge();
      }
    } catch {
      setInbox(await cachedInbox<Inbox>(EMPTY));
      setOffline(true);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
    // A push that arrives while this screen is open should show up in the list
    // rather than waiting for the shopkeeper to pull down.
    return listenForPush({ onReceived: () => void load() });
  }, [load]);

  async function markAllRead() {
    setBusy(true);
    try {
      await api("/api/v1/inbox/read-all", { method: "POST" });
      await clearBadge();
      await load();
    } catch {
      setOffline(true);
    } finally {
      setBusy(false);
    }
  }

  async function open(item: Item) {
    if (!item.readAt) {
      try {
        await api(`/api/v1/inbox/${item.id}/read`, { method: "POST" });
        await load();
      } catch {
        // Reading is not worth blocking navigation over.
      }
    }
    if (item.link) {
      router.push(item.link as never);
    }
  }

  return (
    <View style={styles.page}>
      <Pressable onPress={() => router.back()} style={{ marginBottom: 8 }}>
        <Text style={{ color: colors.soft, fontWeight: "700" }}>Back</Text>
      </Pressable>
      <Text style={styles.title}>Inbox</Text>
      <Text style={styles.sub}>
        {offline ? `Showing saved copy · ${inbox.unread} unread` : `${inbox.unread} unread`}
      </Text>
      <Pressable
        style={[styles.ghost, (busy || inbox.unread === 0) && styles.ghostOff]}
        disabled={busy || inbox.unread === 0}
        onPress={() => void markAllRead()}
      >
        <Text style={styles.ghostText}>{busy ? "Marking…" : "Mark all read"}</Text>
      </Pressable>
      {loading ? (
        <ActivityIndicator color={colors.ink} style={{ marginTop: 24 }} />
      ) : (
        <FlatList
          data={inbox.items}
          keyExtractor={(item) => item.id}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              tintColor={colors.soft}
              onRefresh={() => {
                setRefreshing(true);
                void load();
              }}
            />
          }
          ListEmptyComponent={
            <Text style={styles.empty}>
              {offline
                ? "No saved notifications on this phone yet."
                : "Nothing yet. Alerts about stock, repairs and payments arrive here."}
            </Text>
          }
          renderItem={({ item }) => (
            <Pressable style={[styles.card, !item.readAt && styles.cardUnread]} onPress={() => void open(item)}>
              <View style={styles.cardHead}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                {item.readAt ? null : <View style={styles.dot} />}
              </View>
              {item.body ? <Text style={styles.cardBody}>{item.body}</Text> : null}
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg, padding: 22, paddingTop: 72 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    sub: { color: colors.soft, marginBottom: 12 },
    ghost: { borderColor: colors.line, borderWidth: 1, borderRadius: 14, padding: 12, alignItems: "center", marginBottom: 12 },
    ghostOff: { opacity: 0.45 },
    ghostText: { fontWeight: "700", color: colors.ink },
    card: { backgroundColor: colors.card, borderRadius: 12, padding: 14, marginBottom: 8, borderColor: colors.line, borderWidth: 1 },
    cardUnread: { borderColor: colors.ink },
    cardHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
    cardTitle: { fontWeight: "700", color: colors.ink, flexShrink: 1 },
    cardBody: { color: colors.soft, marginTop: 4 },
    dot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.ink },
    empty: { color: colors.soft, marginTop: 20, lineHeight: 20 },
  });
}
