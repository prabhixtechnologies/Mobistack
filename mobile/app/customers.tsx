import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../lib/api";
import { cachedCustomers, type CachedCustomer } from "../lib/offline";
import { enqueue } from "../lib/outbox";
import { money, useTheme } from "../lib/theme";

export default function CustomersScreen() {
  const { colors } = useTheme();
  const [rows, setRows] = useState<CachedCustomer[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [offline, setOffline] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const styles = makeStyles(colors);

  async function load() {
    try {
      const page = await api<{ content: CachedCustomer[] }>("/api/v1/customers?size=40");
      setRows(page.content);
      setOffline(false);
    } catch {
      setRows(await cachedCustomers());
      setOffline(true);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>Customers</Text>
      {offline ? <Text style={styles.banner}>Customer list is from this phone. New names queue until sync.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput style={styles.search} placeholder="Name" placeholderTextColor={colors.faint} value={name} onChangeText={setName} />
      <TextInput style={styles.search} placeholder="Phone" placeholderTextColor={colors.faint} value={phone} onChangeText={setPhone} />
      <Pressable
        style={styles.btn}
        onPress={async () => {
          const payload = { name, phone, customerType: "RETAIL" };
          try {
            await api("/api/v1/customers", { method: "POST", body: JSON.stringify(payload) });
          } catch {
            await enqueue({
              type: "CUSTOMER",
              idempotencyKey: `${Date.now()}-${phone || name}`,
              customer: payload,
            });
            setRows((current) => [{ id: `offline-${Date.now()}`, name, phone, outstandingAmount: 0 }, ...current]);
          }
          setName("");
          setPhone("");
          setError(null);
          await load();
        }}
      >
        <Text style={styles.btnText}>Add</Text>
      </Pressable>
      {rows.map((row) => (
        <View key={row.id} style={styles.card}>
          <Text style={styles.name}>{row.name}</Text>
          <Text style={styles.sub}>{row.phone ?? "No phone"} · {money(row.outstandingAmount)}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62 },
    title: { fontSize: 32, fontWeight: "500", marginBottom: 14, color: colors.ink },
    banner: { color: colors.warn, marginBottom: 12, fontWeight: "600" },
    error: { color: colors.bad, marginBottom: 8 },
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 16,
      padding: 14,
      marginBottom: 10,
      color: colors.ink,
    },
    card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginBottom: 10 },
    name: { fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 4 },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center", marginBottom: 16 },
    btnText: { color: colors.bg, fontWeight: "700" },
  });
}
