import { useCallback, useEffect, useState } from "react";
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import {
  cachedSales,
  cachedVariants,
  searchVariants,
  syncNow,
  type CachedSale,
  type CachedVariant,
} from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
import { useAction } from "../../lib/useAction";
import { useDebounced } from "../../lib/useDebounced";
import { useScreenData } from "../../lib/useScreenData";
import { BarcodeScanButton } from "../../components/BarcodeScan";
import { Empty, Failed, Loading, OfflineNotice, Problem } from "../../components/ListState";
import { money, useTheme } from "../../lib/theme";

interface Line {
  variantId: string;
  name: string;
  quantity: number;
  unitPrice: number;
}

export default function SalesScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [query, setQuery] = useState("");
  const settled = useDebounced(query, 200);
  const [hits, setHits] = useState<CachedVariant[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [queued, setQueued] = useState<CachedSale[]>([]);
  const [method, setMethod] = useState<"CASH" | "UPI" | "CARD">("CASH");

  const load = useCallback(async () => {
    const page = await api<{ content: CachedSale[] }>("/api/v1/sales?size=20");
    return page.content;
  }, []);
  const sales = useScreenData<CachedSale[]>("sales", load, { fallback: cachedSales });

  useEffect(() => {
    void syncNow();
  }, []);

  useEffect(() => {
    const term = settled.trim();
    if (term.length < 2) {
      setHits([]);
      return;
    }
    let live = true;
    api<{ parts: { variantId: string; productName: string; sku: string; availableQty: number; price: number }[] }>(
      `/api/v1/search?q=${encodeURIComponent(term)}`,
    )
      .then((result) => {
        if (!live) {
          return;
        }
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
        );
      })
      .catch(async () => {
        const local = searchVariants(await cachedVariants(), term).slice(0, 12);
        if (live) {
          setHits(local);
        }
      });
    return () => {
      live = false;
    };
  }, [settled]);

  const total = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);

  const checkout = useAction(
    async () => {
      const payload = {
        pricingFlag: "NORMAL",
        idempotencyKey: `${Date.now()}-${Math.random()}`,
        items: lines.map((line) => ({
          variantId: line.variantId,
          quantity: line.quantity,
          unitPrice: line.unitPrice,
        })),
        payments: [{ method, amount: total }],
      };
      try {
        await api("/api/v1/sales", { method: "POST", body: JSON.stringify(payload) });
      } catch {
        // Queued rather than lost. The key is what stops the flush double-posting.
        await enqueue({ type: "SALE", idempotencyKey: payload.idempotencyKey, sale: payload });
        setQueued((current) => [
          { id: payload.idempotencyKey, invoiceNumber: "Waiting to sync", total, status: "QUEUED" },
          ...current,
        ]);
      }
      setLines([]);
      setQuery("");
      setHits([]);
      sales.refresh();
    },
    { fallbackError: "Could not complete that sale." },
  );

  const history = [...queued, ...(sales.data ?? [])];

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={styles.page}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        <RefreshControl refreshing={sales.refreshing} onRefresh={sales.refresh} tintColor={colors.accent} />
      }
    >
      <Text style={styles.title}>Sales</Text>
      <Text style={styles.copy}>
        Type a SKU or barcode. Complete writes the ledger; offline sales wait on this phone until the radio returns.
      </Text>
      {sales.offline ? <OfflineNotice what="recent sales" /> : null}
      {checkout.error ? <Problem message={checkout.error} /> : null}

      <BarcodeScanButton onScan={setQuery} />
      <TextInput
        style={styles.search}
        placeholder="Part, SKU, barcode"
        placeholderTextColor={colors.faint}
        value={query}
        onChangeText={setQuery}
        autoCorrect={false}
      />
      {query.trim().length >= 2 && hits.length === 0 ? (
        <Text style={styles.sub}>No part matches that. Try a shorter search or scan the box.</Text>
      ) : null}
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
              return [
                ...current,
                { variantId: hit.id, name: hit.productName, quantity: 1, unitPrice: hit.retailPrice },
              ];
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
          <View style={{ flexDirection: "row", gap: 20, marginTop: 8 }}>
            <Pressable
              hitSlop={10}
              onPress={() =>
                setLines((current) =>
                  current.map((row) =>
                    row.variantId === line.variantId ? { ...row, quantity: Math.max(1, row.quantity - 1) } : row,
                  ),
                )
              }
            >
              <Text style={{ fontWeight: "700", color: colors.ink, fontSize: 18 }}>−</Text>
            </Pressable>
            <Pressable
              hitSlop={10}
              onPress={() =>
                setLines((current) =>
                  current.map((row) =>
                    row.variantId === line.variantId ? { ...row, quantity: row.quantity + 1 } : row,
                  ),
                )
              }
            >
              <Text style={{ fontWeight: "700", color: colors.ink, fontSize: 18 }}>+</Text>
            </Pressable>
            <Pressable
              hitSlop={10}
              onPress={() => setLines((current) => current.filter((row) => row.variantId !== line.variantId))}
            >
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

      <Pressable
        style={[styles.btn, (checkout.busy || lines.length === 0) && { opacity: 0.5 }]}
        onPress={() => void checkout.run()}
        disabled={checkout.busy || lines.length === 0}
      >
        <Text style={styles.btnText}>
          {checkout.busy ? "Taking payment…" : `Take ${money(total)} · ${method}`}
        </Text>
      </Pressable>

      <Text style={styles.section}>Today</Text>
      {sales.loading && history.length === 0 ? (
        <Loading label="Loading today's sales…" />
      ) : sales.error && history.length === 0 ? (
        <Failed message={sales.error} onRetry={sales.refresh} />
      ) : history.length === 0 ? (
        <Empty title="No sales yet" hint="Add a part above and take the payment. Invoices land here." />
      ) : (
        history.map((sale) => (
          <View key={sale.id} style={styles.card}>
            <Text style={styles.name}>{sale.invoiceNumber}</Text>
            <Text style={styles.sub}>
              {sale.status} · {money(sale.total)}
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
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    copy: { color: colors.soft, marginTop: 8, marginBottom: 16, lineHeight: 22 },
    section: { color: colors.faint, fontWeight: "700", marginTop: 20, marginBottom: 8 },
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
  });
}
