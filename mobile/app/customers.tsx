import { useCallback, useMemo, useState } from "react";
import { StyleSheet, Text, TextInput } from "react-native";
import { api } from "../lib/api";
import { cachedCustomers, type CachedCustomer } from "../lib/offline";
import { enqueue } from "../lib/outbox";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { useScreenData } from "../lib/useScreenData";
import { Empty, Failed, Loading, OfflineNotice, Problem } from "../components/ListState";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { money, useTheme } from "../lib/theme";

export default function CustomersScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [search, setSearch] = useState("");
  const settled = useDebounced(search);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [queued, setQueued] = useState<CachedCustomer[]>([]);

  const term = settled.trim();
  const load = useCallback(async () => {
    const query = term ? `q=${encodeURIComponent(term)}&` : "";
    const page = await api<{ content: CachedCustomer[] }>(`/api/v1/customers?${query}size=40`);
    return page.content;
  }, [term]);
  const fallback = useCallback(async () => {
    const rows = await cachedCustomers();
    if (!term) {
      return rows;
    }
    const needle = term.toLowerCase();
    return rows.filter(
      (row) => row.name.toLowerCase().includes(needle) || (row.phone ?? "").includes(term),
    );
  }, [term]);
  const customers = useScreenData<CachedCustomer[]>(term, load, { fallback });

  const add = useAction(
    async () => {
      const payload = { name: name.trim(), phone: phone.trim(), customerType: "RETAIL" };
      try {
        await api("/api/v1/customers", { method: "POST", body: JSON.stringify(payload) });
      } catch {
        await enqueue({
          type: "CUSTOMER",
          idempotencyKey: `${Date.now()}-${payload.phone || payload.name}`,
          customer: payload,
        });
        setQueued((current) => [
          { id: `offline-${Date.now()}`, name: payload.name, phone: payload.phone, outstandingAmount: 0 },
          ...current,
        ]);
      }
      setName("");
      setPhone("");
      customers.refresh();
    },
    { fallbackError: "Could not save that customer." },
  );

  const rows = useMemo(() => [...queued, ...(customers.data ?? [])], [queued, customers.data]);

  return (
    <Screen
      title="Customers"
      copy="Walk-ins stay unnamed. Regulars keep a phone and a balance."
      back
      onRefresh={customers.refresh}
      refreshing={customers.refreshing}
    >
      {customers.offline ? <OfflineNotice what="customer list" /> : null}
      {add.error ? <Problem message={add.error} /> : null}

      <TextInput
        style={styles.search}
        placeholder="Name"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Customer name"
        value={name}
        onChangeText={setName}
      />
      <TextInput
        style={styles.search}
        placeholder="Phone"
        placeholderTextColor={colors.faint}
        keyboardType="phone-pad"
        accessibilityLabel="Customer phone"
        value={phone}
        onChangeText={setPhone}
      />
      <PrimaryButton
        label={add.busy ? "Saving…" : "Add"}
        disabled={add.busy || !name.trim()}
        onPress={() => void add.run()}
      />

      <TextInput
        style={styles.search}
        placeholder="Search name or phone"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Search customers"
        value={search}
        onChangeText={setSearch}
        autoCorrect={false}
      />

      {customers.loading && rows.length === 0 ? (
        <Loading label="Loading customers…" />
      ) : customers.error && rows.length === 0 ? (
        <Failed message={customers.error} onRetry={customers.refresh} />
      ) : rows.length === 0 ? (
        <Empty
          title={term ? "No customer matches that" : "No customers yet"}
          hint={
            term
              ? "Try part of the name, or the last few digits of the number."
              : "Add a regular above so their balance follows them."
          }
        />
      ) : (
        rows.map((row) => (
          <Card key={row.id}>
            <Text style={styles.name}>{row.name}</Text>
            <Text style={styles.sub}>
              {row.phone || "No phone"} · {money(row.outstandingAmount)}
            </Text>
          </Card>
        ))
      )}
    </Screen>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 16,
      padding: 14,
      marginBottom: 10,
      color: colors.ink,
    },
    name: { fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 4 },
  });
}
