import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { api } from "../lib/api";
import { cachedInbox, saveInbox } from "../lib/offline";
import { useTheme } from "../lib/theme";

interface Item {
  id: string;
  title: string;
  body?: string;
  readAt?: string | null;
}

export default function InboxScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [items, setItems] = useState<Item[]>([]);
  const [unread, setUnread] = useState(0);
  const [offline, setOffline] = useState(false);

  async function load() {
    try {
      const inbox = await api<{ unread: number; items: Item[] }>("/api/v1/inbox");
      setItems(inbox.items);
      setUnread(inbox.unread);
      setOffline(false);
      await saveInbox(inbox);
    } catch {
      const inbox = await cachedInbox<{ unread: number; items: Item[] }>({ unread: 0, items: [] });
      setItems(inbox.items);
      setUnread(inbox.unread);
      setOffline(true);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <View style={styles.page}>
      <Text style={styles.title}>Inbox</Text>
      <Text style={styles.sub}>{offline ? `Offline · ${unread} unread` : `${unread} unread`}</Text>
      <Pressable style={styles.ghost} onPress={() => void api("/api/v1/inbox/read-all", { method: "POST" }).then(load)}>
        <Text style={styles.ghostText}>Mark all read</Text>
      </Pressable>
      <ScrollView>
        {items.map((item) => (
          <View key={item.id} style={styles.card}>
            <Text style={styles.cardTitle}>{item.title}</Text>
            <Text style={styles.cardBody}>{item.body}</Text>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg, padding: 22, paddingTop: 72 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    sub: { color: colors.soft, marginBottom: 12 },
    ghost: { borderColor: colors.line, borderWidth: 1, borderRadius: 14, padding: 12, alignItems: "center", marginBottom: 12 },
    ghostText: { fontWeight: "700", color: colors.ink },
    card: { backgroundColor: colors.card, borderRadius: 12, padding: 14, marginBottom: 8, borderColor: colors.line, borderWidth: 1 },
    cardTitle: { fontWeight: "700", color: colors.ink },
    cardBody: { color: colors.soft, marginTop: 4 },
  });
}
