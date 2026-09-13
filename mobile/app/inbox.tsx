import { useCallback, useEffect, useState } from "react";
import { AppState, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import { router } from "expo-router";
import { api } from "../lib/api";
import { cachedInbox, saveInbox } from "../lib/offline";
import { clearBadge, listenForPush } from "../lib/push";
import { safeAppPath } from "../lib/safePath";
import { useTheme } from "../lib/theme";
import { Empty, Failed, Loading, OfflineNotice } from "../components/ListState";

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
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const fresh = await api<Inbox>("/api/v1/inbox");
      setInbox(fresh);
      setOffline(false);
      setError(null);
      await saveInbox(fresh);
      if (fresh.unread === 0) {
        await clearBadge();
      }
    } catch (cause) {
      setInbox(await cachedInbox<Inbox>(EMPTY));
      setOffline(true);
      setError(cause instanceof Error ? cause.message : "Could not load inbox.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        void load();
      }
    });
    // A push that arrives while this screen is open should show up in the list
    // rather than waiting for the shopkeeper to pull down.
    const stopPush = listenForPush({ onReceived: () => void load() });
    return () => {
      sub.remove();
      stopPush();
    };
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
      const path = safeAppPath(item.link, "");
      if (path) {
        router.push(path as never);
      }
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={styles.head}>
        <Pressable
          onPress={() => router.back()}
          style={{ marginBottom: 8, minHeight: 44, justifyContent: "center" }}
          hitSlop={12}
        >
          <Text style={{ color: colors.soft, fontWeight: "700" }}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Inbox</Text>
        <Text style={styles.copy}>
          {offline
            ? `Showing the copy saved on this phone. ${inbox.unread} unread.`
            : inbox.unread === 0
              ? "You are caught up. Shop notices land here."
              : `${inbox.unread} unread ${inbox.unread === 1 ? "notice" : "notices"} waiting.`}
        </Text>
        {offline ? <OfflineNotice what="inbox" /> : null}
        <Pressable
          style={[styles.ghost, (busy || inbox.unread === 0) && styles.ghostOff]}
          disabled={busy || inbox.unread === 0}
          hitSlop={12}
          onPress={() => void markAllRead()}
        >
          <Text style={styles.ghostText}>{busy ? "Marking…" : "Mark all read"}</Text>
        </Pressable>
      </View>
      {loading ? (
        <Loading label="Loading inbox…" />
      ) : error && inbox.items.length === 0 && !offline ? (
        <Failed message={error} onRetry={() => void load()} />
      ) : (
        <FlatList
          data={inbox.items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              tintColor={colors.accent}
              onRefresh={() => {
                setRefreshing(true);
                void load();
              }}
            />
          }
          ListEmptyComponent={
            <Empty
              title={offline ? "Nothing saved on this phone" : "Nothing in the inbox yet"}
              hint={
                offline
                  ? "Connect once so join approvals, billing, and shop alerts can be read offline."
                  : "Alerts about stock, repairs and payments arrive here."
              }
            />
          }
          renderItem={({ item }) => (
            <Pressable
              style={[styles.card, !item.readAt && styles.cardUnread, { minHeight: 44 }]}
              hitSlop={12}
              onPress={() => void open(item)}
            >
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
    head: { paddingHorizontal: 22, paddingTop: 62 },
    title: { fontSize: 32, fontWeight: "600", letterSpacing: -0.6, color: colors.ink, marginBottom: 8 },
    copy: { color: colors.soft, marginBottom: 16, lineHeight: 22 },
    list: { paddingHorizontal: 22, paddingBottom: 48 },
    ghost: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 14,
      padding: 12,
      alignItems: "center",
      marginBottom: 12,
      minHeight: 44,
      justifyContent: "center",
    },
    ghostOff: { opacity: 0.45 },
    ghostText: { fontWeight: "700", color: colors.ink },
    card: {
      backgroundColor: colors.card,
      borderRadius: 12,
      padding: 14,
      marginBottom: 8,
      borderColor: colors.line,
      borderWidth: 1,
    },
    cardUnread: { borderColor: colors.ink },
    cardHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
    cardTitle: { fontWeight: "700", color: colors.ink, flexShrink: 1 },
    cardBody: { color: colors.soft, marginTop: 4 },
    dot: { width: 9, height: 9, borderRadius: 5, backgroundColor: colors.accent },
  });
}
