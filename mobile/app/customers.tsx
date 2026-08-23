import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../lib/api";
import { money, useTheme } from "../lib/theme";

interface Customer {
  id: string;
  name: string;
  phone?: string;
  outstandingAmount: number;
}

export default function CustomersScreen() {
  const { colors } = useTheme();
  const [rows, setRows] = useState<Customer[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const styles = makeStyles(colors);

  async function load() {
    const page = await api<{ content: Customer[] }>("/api/v1/customers?size=40");
    setRows(page.content);
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>Customers</Text>
      <TextInput style={styles.search} placeholder="Name" placeholderTextColor={colors.faint} value={name} onChangeText={setName} />
      <TextInput style={styles.search} placeholder="Phone" placeholderTextColor={colors.faint} value={phone} onChangeText={setPhone} />
      <Pressable
        style={styles.btn}
        onPress={async () => {
          await api("/api/v1/customers", { method: "POST", body: JSON.stringify({ name, phone, customerType: "RETAIL" }) });
          setName("");
          setPhone("");
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
