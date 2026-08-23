import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedSales, cachedVariants, searchVariants, type CachedSale, type CachedVariant } from "../../lib/offline";
import { enqueue, flush } from "../../lib/outbox";
import { BarcodeScanButton } from "../../components/BarcodeScan";
import { money, useTheme } from "../../lib/theme";

interface Line {
  variantId: string;
  name: string;
  quantity: number;
  unitPrice: number;
}

export default function SalesScreen() {
  const { colors } = useTheme();
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<CachedVariant[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [sales, setSales] = useState<CachedSale[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [method, setMethod] = useState<"CASH" | "UPI" | "CARD">("CASH");
  const styles = makeStyles(colors);

  async function load() {
    try {
      const page = await api<{ content: CachedSale[] }>("/api/v1/sales?size=20");
      setSales(page.content);
      setOffline(false);
    } catch {
      setSales(await cachedSales());
      setOffline(true);
    }
  }

  useEffect(() => {
    void flush().catch(() => undefined);
    load().catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      return;
    }
    const handle = setTimeout(() => {
      api<{ parts: { variantId: string; productName: string; sku: string; availableQty: number; price: number }[] }>(
        `/api/v1/search?q=${encodeURIComponent(query)}`,
      )
        .then((result) =>
          setHits(
            result.parts.map((part) => ({
              id: part.variantId,
              productName: part.productName,
              variantName: part.sku,
              sku: part.sku,
              availableQty: part.availableQty,
              retailPrice: part.price,
              stockStatus: "GREEN",
            })),
          ),
        )
        .catch(async () => {
          setHits(searchVariants(await cachedVariants(), query).slice(0, 12));
        });
    }, 160);
    return () => clearTimeout(handle);
  }, [query]);

  const total = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);

  async function checkout() {
    const payload = {
      pricingFlag: "NORMAL",
      idempotencyKey: `${Date.now()}-${Math.random()}`,
      items: lines.map((line) => ({ variantId: line.variantId, quantity: line.quantity, unitPrice: line.unitPrice })),
      payments: [{ method, amount: total }],
    };
    try {
      await api("/api/v1/sales", { method: "POST", body: JSON.stringify(payload) });
    } catch {
      await enqueue({ type: "SALE", idempotencyKey: payload.idempotencyKey, sale: payload });
      setSales((current) => [
        { id: payload.idempotencyKey, invoiceNumber: "OFFLINE", total, status: "QUEUED" },
        ...current,
      ]);
    }
    setLines([]);
    setQuery("");
    await load();
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>Sales</Text>
      <Text style={styles.copy}>
        Type a SKU or barcode. Complete writes the ledger; offline sales wait in SQLite until the radio returns.
      </Text>
      {offline ? <Text style={styles.banner}>Counter is offline. Sales are queued on this device.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <BarcodeScanButton onScan={setQuery} />
      <TextInput
        style={styles.search}
        placeholder="Part, SKU, barcode"
        placeholderTextColor={colors.faint}
        value={query}
        onChangeText={setQuery}
      />
      {hits.map((hit) => (
        <Pressable
          key={hit.id}
          style={styles.card}
          onPress={() => {
            setLines((current) => {
              const existing = current.find((line) => line.variantId === hit.id);
              if (existing) {
                return current.map((line) =>
                  line.variantId === hit.id ? { ...line, quantity: line.quantity + 1 } : line,
                );
              }
              return [...current, { variantId: hit.id, name: hit.productName, quantity: 1, unitPrice: hit.retailPrice }];
            });
            setQuery("");
            setHits([]);
          }}
        >
          <Text style={styles.name}>{hit.productName}</Text>
          <Text style={styles.sub}>
            {hit.sku} · {hit.availableQty} · {money(hit.retailPrice)}
          </Text>
        </Pressable>
      ))}
      {lines.map((line) => (
        <View key={line.variantId} style={styles.card}>
          <Text style={styles.name}>{line.name}</Text>
          <Text style={styles.sub}>
            {line.quantity} × {money(line.unitPrice)}
          </Text>
          <View style={{ flexDirection: "row", gap: 16, marginTop: 8 }}>
            <Pressable onPress={() => setLines((current) => current.map((row) => row.variantId === line.variantId ? { ...row, quantity: Math.max(1, row.quantity - 1) } : row))}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>−</Text>
            </Pressable>
            <Pressable onPress={() => setLines((current) => current.map((row) => row.variantId === line.variantId ? { ...row, quantity: row.quantity + 1 } : row))}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>+</Text>
            </Pressable>
            <Pressable onPress={() => setLines((current) => current.filter((row) => row.variantId !== line.variantId))}>
              <Text style={{ fontWeight: "700", color: colors.bad }}>Remove</Text>
            </Pressable>
          </View>
        </View>
      ))}
      <View style={{ flexDirection: "row", gap: 8, marginBottom: 8 }}>
        {(["CASH", "UPI", "CARD"] as const).map((item) => (
          <Pressable
            key={item}
            onPress={() => setMethod(item)}
            style={{
              borderWidth: 1,
              borderColor: method === item ? colors.ink : colors.line,
              backgroundColor: method === item ? colors.ink : colors.card,
              borderRadius: 999,
              paddingHorizontal: 12,
              paddingVertical: 6,
            }}
          >
            <Text style={{ color: method === item ? colors.bg : colors.ink, fontWeight: "700" }}>{item}</Text>
          </Pressable>
        ))}
      </View>
      <Pressable style={styles.btn} onPress={() => void checkout()} disabled={lines.length === 0}>
        <Text style={styles.btnText}>Take {money(total)} · {method}</Text>
      </Pressable>
      {sales.map((sale) => (
        <View key={sale.id} style={styles.card}>
          <Text style={styles.name}>{sale.invoiceNumber}</Text>
          <Text style={styles.sub}>
            {sale.status} · {money(sale.total)}
          </Text>
        </View>
      ))}
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 40 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    copy: { color: colors.soft, marginTop: 8, marginBottom: 16, lineHeight: 22 },
    banner: { color: colors.warn, marginBottom: 12, fontWeight: "600" },
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 16,
      padding: 14,
      marginBottom: 12,
      color: colors.ink,
    },
    card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginBottom: 10 },
    name: { fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 4 },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center", marginVertical: 8 },
    btnText: { color: colors.bg, fontWeight: "700" },
    error: { color: colors.bad, marginBottom: 8 },
  });
}
