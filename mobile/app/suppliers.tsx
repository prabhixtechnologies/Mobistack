import { useEffect, useState } from "react";
import { Text, TextInput } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { api } from "../lib/api";
import { money, useTheme } from "../lib/theme";

interface Supplier {
  id: string;
  name: string;
  phone?: string;
  city?: string;
  outstandingAmount?: number;
}

export default function SuppliersScreen() {
  const { colors } = useTheme();
  const [rows, setRows] = useState<Supplier[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [city, setCity] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const page = await api<{ content: Supplier[] }>("/api/v1/suppliers?size=40");
    setRows(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  return (
    <Screen title="Suppliers" copy="People you buy parts from." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      <TextInput style={field(colors)} placeholder="Name" placeholderTextColor={colors.faint} value={name} onChangeText={setName} />
      <TextInput style={field(colors)} placeholder="Phone" placeholderTextColor={colors.faint} value={phone} onChangeText={setPhone} />
      <TextInput style={field(colors)} placeholder="City" placeholderTextColor={colors.faint} value={city} onChangeText={setCity} />
      <PrimaryButton
        label="Add supplier"
        disabled={!name.trim()}
        onPress={() => {
          void api("/api/v1/suppliers", {
            method: "POST",
            body: JSON.stringify({ name: name.trim(), phone, city }),
          })
            .then(() => {
              setName("");
              setPhone("");
              setCity("");
              return load();
            })
            .catch((err: Error) => setError(err.message));
        }}
      />
      {rows.map((row) => (
        <Card key={row.id}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{row.name}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {[row.phone, row.city].filter(Boolean).join(" · ") || "No contact"}
            {` · due ${money(row.outstandingAmount ?? 0)}`}
          </Text>
        </Card>
      ))}
    </Screen>
  );
}

function field(colors: ReturnType<typeof useTheme>["colors"]) {
  return {
    backgroundColor: colors.card,
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    color: colors.ink,
  };
}
