import { useCallback, useState } from "react";
import { Text, TextInput } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { Empty, Failed, Loading, Problem } from "../components/ListState";
import { api } from "../lib/api";
import { useAction } from "../lib/useAction";
import { useScreenData } from "../lib/useScreenData";
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
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [city, setCity] = useState("");

  const load = useCallback(async () => {
    const page = await api<{ content: Supplier[] }>("/api/v1/suppliers?size=40");
    return page.content;
  }, []);
  const suppliers = useScreenData<Supplier[]>("suppliers", load);

  const add = useAction(
    async () => {
      await api("/api/v1/suppliers", {
        method: "POST",
        body: JSON.stringify({ name: name.trim(), phone, city }),
      });
      setName("");
      setPhone("");
      setCity("");
      suppliers.refresh();
    },
    { fallbackError: "Could not save that supplier." },
  );

  const rows = suppliers.data ?? [];

  return (
    <Screen
      title="Suppliers"
      copy="People you buy parts from."
      back
      onRefresh={suppliers.refresh}
      refreshing={suppliers.refreshing}
    >
      {add.error ? <Problem message={add.error} /> : null}
      <TextInput
        style={field(colors)}
        placeholder="Name"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Supplier name"
        value={name}
        onChangeText={setName}
      />
      <TextInput
        style={field(colors)}
        placeholder="Phone"
        placeholderTextColor={colors.faint}
        keyboardType="phone-pad"
        accessibilityLabel="Supplier phone"
        value={phone}
        onChangeText={setPhone}
      />
      <TextInput
        style={field(colors)}
        placeholder="City"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Supplier city"
        value={city}
        onChangeText={setCity}
      />
      <PrimaryButton
        label={add.busy ? "Saving…" : "Add supplier"}
        disabled={add.busy || !name.trim()}
        onPress={() => void add.run()}
      />

      {suppliers.loading && rows.length === 0 ? (
        <Loading label="Loading suppliers…" />
      ) : suppliers.error ? (
        <Failed message={suppliers.error} onRetry={suppliers.refresh} />
      ) : rows.length === 0 ? (
        <Empty title="No suppliers yet" hint="Add the people you buy from so purchases post against them." />
      ) : (
        rows.map((row) => (
          <Card key={row.id}>
            <Text style={{ fontWeight: "700", color: colors.ink }}>{row.name}</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              {[row.phone, row.city].filter(Boolean).join(" · ") || "No contact"}
              {` · due ${money(row.outstandingAmount ?? 0)}`}
            </Text>
          </Card>
        ))
      )}
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
