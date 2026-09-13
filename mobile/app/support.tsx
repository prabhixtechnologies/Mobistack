import { useEffect, useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../lib/api";
import { cachedSupport, saveSupport } from "../lib/offline";
import { useTheme } from "../lib/theme";
import { BRAND } from "../lib/brand";
import { Empty, Problem } from "../components/ListState";
import { PrimaryButton, Screen } from "../components/Screen";

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

  const messages = conversation?.messages ?? [];

  return (
    <Screen
      title="Support"
      copy={`${BRAND.organization} · ${BRAND.publicOrigin ?? "mobistack.prabhixtechnologies.com"}`}
      back
    >
      {messages.length === 0 ? (
        <Empty
          title="No messages yet"
          hint="Ask about stock, sales, or talk to a person."
        />
      ) : (
        messages.map((row) => (
          <View key={row.id} style={styles.bubble}>
            <Text style={styles.meta}>{row.authorType}</Text>
            <Text style={styles.body}>{row.body}</Text>
          </View>
        ))
      )}
      {error ? <Problem message={error} /> : null}
      <TextInput
        style={styles.input}
        value={message}
        onChangeText={setMessage}
        placeholder="Ask about stock, sales, or talk to a person"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Support message"
      />
      <PrimaryButton label="Send" onPress={() => void send()} />
    </Screen>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    bubble: {
      backgroundColor: colors.card,
      borderRadius: 12,
      padding: 12,
      marginBottom: 8,
      borderColor: colors.line,
      borderWidth: 1,
    },
    meta: { color: colors.faint, fontSize: 12, marginBottom: 4 },
    body: { color: colors.ink },
    input: {
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 12,
      padding: 12,
      color: colors.ink,
      marginBottom: 10,
      minHeight: 44,
    },
  });
}
