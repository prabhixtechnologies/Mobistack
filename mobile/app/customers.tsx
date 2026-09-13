import { useCallback, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { api } from "../lib/api";
import { cachedCustomers, type CachedCustomer } from "../lib/offline";
import { enqueue } from "../lib/outbox";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { useScreenData } from "../lib/useScreenData";
import { Empty, Failed, Loading, OfflineNotice, Problem } from "../components/ListState";
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
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={styles.page}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        <RefreshControl refreshing={customers.refreshing} onRefresh={customers.refresh} tintColor={colors.accent} />
      }
    >
      <Pressable onPress={() => router.back()} style={{ marginBottom: 8 }} hitSlop={8}>
        <Text style={{ color: colors.soft, fontWeight: "700" }}>Back</Text>
      </Pressable>
      <Text style={styles.title}>Customers</Text>
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
      <Pressable
        style={[styles.btn, (add.busy || !name.trim()) && { opacity: 0.5 }]}
        disabled={add.busy || !name.trim()}
        onPress={() => void add.run()}
      >
        <Text style={styles.btnText}>{add.busy ? "Saving…" : "Add"}</Text>
      </Pressable>

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
          <View key={row.id} style={styles.card}>
            <Text style={styles.name}>{row.name}</Text>
            <Text style={styles.sub}>
              {row.phone || "No phone"} · {money(row.outstandingAmount)}
            </Text>
          </View>
        ))
      )}
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 40 },
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
