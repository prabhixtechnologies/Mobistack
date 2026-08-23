import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { api } from "../lib/api";
import { cachedSupport, saveSupport } from "../lib/offline";
import { useTheme } from "../lib/theme";
import { BRAND } from "../lib/brand";

interface Message {
  id: string;
  authorType: string;
  body: string;
}

interface Conversation {
  id: string;
  messages: Message[];
}

export default function SupportScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Conversation[]>("/api/v1/support/conversations")
      .then(async (rows) => {
        const first = rows[0] ?? null;
        setConversation(first);
        if (first) {
          await saveSupport(first);
        }
      })
      .catch(async (err: Error) => {
        const cached = await cachedSupport<Conversation | null>(null);
        if (cached) {
          setConversation(cached);
        } else {
          setError(err.message);
        }
      });
  }, []);

  async function send() {
    if (!message.trim()) {
      return;
    }
    try {
      const next = conversation
        ? await api<Conversation>(`/api/v1/support/conversations/${conversation.id}/messages`, {
            method: "POST",
            body: JSON.stringify({ message, channel: "MOBILE" }),
          })
        : await api<Conversation>("/api/v1/support/chat", {
            method: "POST",
            body: JSON.stringify({ message, channel: "MOBILE" }),
          });
      setConversation(next);
      setMessage("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send");
    }
  }

  return (
    <View style={styles.page}>
      <Pressable onPress={() => router.back()} style={{ marginBottom: 8 }}>
        <Text style={{ color: colors.soft, fontWeight: "700" }}>Back</Text>
      </Pressable>
      <Text style={styles.title}>Support</Text>
      <Text style={styles.sub}>{BRAND.organization} · {BRAND.publicOrigin ?? "mobistack.prabhixtechnologies.com"}</Text>
      <ScrollView style={styles.log}>
        {(conversation?.messages ?? []).map((row) => (
          <View key={row.id} style={styles.bubble}>
            <Text style={styles.meta}>{row.authorType}</Text>
            <Text style={styles.body}>{row.body}</Text>
          </View>
        ))}
      </ScrollView>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput style={styles.input} value={message} onChangeText={setMessage} placeholder="Ask about stock, sales, or talk to a person" placeholderTextColor={colors.faint} />
      <Pressable style={styles.btn} onPress={() => void send()}>
        <Text style={styles.btnText}>Send</Text>
      </Pressable>
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { flex: 1, backgroundColor: colors.bg, padding: 22, paddingTop: 72 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    sub: { color: colors.soft, marginTop: 6, marginBottom: 16 },
    log: { flex: 1 },
    bubble: { backgroundColor: colors.card, borderRadius: 12, padding: 12, marginBottom: 8, borderColor: colors.line, borderWidth: 1 },
    meta: { color: colors.faint, fontSize: 12, marginBottom: 4 },
    body: { color: colors.ink },
    error: { color: "#b42318", marginBottom: 8 },
    input: { borderColor: colors.line, borderWidth: 1, borderRadius: 12, padding: 12, color: colors.ink, marginBottom: 10 },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center" },
    btnText: { color: colors.bg, fontWeight: "700" },
  });
}
