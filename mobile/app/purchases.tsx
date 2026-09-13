import { useCallback, useRef, useState } from "react";
import { Pressable, Text, TextInput, View } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { Empty, Failed, Loading, Problem } from "../components/ListState";
import { api } from "../lib/api";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { useScreenData } from "../lib/useScreenData";
import { money, useTheme } from "../lib/theme";

interface Purchase {
  id: string;
  supplierName?: string;
  status: string;
  total: number;
  paid: number;
  outstanding: number;
}

interface Supplier {
  id: string;
  name: string;
}

interface Hit {
  variantId: string;
  productName: string;
  sku: string;
  price: number;
}

interface Line {
  variantId: string;
  name: string;
  quantity: number;
  unitCost: number;
}

interface Bundle {
  purchases: Purchase[];
  suppliers: Supplier[];
}

export default function PurchasesScreen() {
  const { colors } = useTheme();
  const [supplierId, setSupplierId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [lines, setLines] = useState<Line[]>([]);

  const load = useCallback(async () => {
    const [page, party] = await Promise.all([
      api<{ content: Purchase[] }>("/api/v1/purchases?size=20"),
      api<{ content: Supplier[] }>("/api/v1/suppliers?size=40"),
    ]);
    return { purchases: page.content, suppliers: party.content };
  }, []);
  const data = useScreenData<Bundle>("purchases", load);
  const suppliers = data.data?.suppliers ?? [];
  const rows = data.data?.purchases ?? [];
  const chosen = supplierId ?? suppliers[0]?.id ?? null;

  const settled = useDebounced(query);
  const term = settled.trim();
  const search = useCallback(async () => {
    if (term.length < 2) {
      return [] as Hit[];
    }
    const result = await api<{ parts: Hit[] }>(`/api/v1/search?q=${encodeURIComponent(term)}`);
    return result.parts ?? [];
  }, [term]);
  const hits = useScreenData<Hit[]>(`search:${term}`, search, { enabled: term.length >= 2 });

  const total = lines.reduce((sum, line) => sum + line.unitCost * line.quantity, 0);

  // One key per basket, so a second press of Receive lands on the same receipt
  // rather than booking the stock in twice.
  const receiptKey = useRef(`${Date.now()}-${Math.random()}`);

  const receive = useAction(
    async () => {
      await api("/api/v1/purchases", {
        method: "POST",
        headers: { "Idempotency-Key": receiptKey.current },
        body: JSON.stringify({
          supplierId: chosen,
          tax: 0,
          idempotencyKey: receiptKey.current,
          items: lines.map((line) => ({
            variantId: line.variantId,
            quantity: line.quantity,
            unitCost: line.unitCost,
          })),
          payments: [{ method: "CASH", amount: total }],
        }),
      });
      setLines([]);
      receiptKey.current = `${Date.now()}-${Math.random()}`;
      data.refresh();
    },
    { fallbackError: "That receipt did not go through. Nothing was booked in." },
  );

  function addLine(hit: Hit) {
    setLines((current) => {
      const existing = current.find((line) => line.variantId === hit.variantId);
      if (existing) {
        return current.map((line) =>
          line.variantId === hit.variantId ? { ...line, quantity: line.quantity + 1 } : line,
        );
      }
      return [...current, { variantId: hit.variantId, name: hit.productName, quantity: 1, unitCost: hit.price }];
    });
    setQuery("");
  }

  return (
    <Screen
      title="Purchases"
      copy="Receive supplier stock onto the same ledger the counter sells from."
      back
      onRefresh={data.refresh}
      refreshing={data.refreshing}
    >
      {receive.error ? <Problem message={receive.error} /> : null}

      {data.loading && !data.data ? (
        <Loading label="Loading suppliers…" />
      ) : data.error && !data.data ? (
        <Failed message={data.error} onRetry={data.refresh} />
      ) : (
        <>
          {suppliers.length === 0 ? (
            <Text style={{ color: colors.soft, marginBottom: 12 }}>Add a supplier first.</Text>
          ) : (
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
              {suppliers.map((supplier) => (
                <Pressable
                  key={supplier.id}
                  onPress={() => setSupplierId(supplier.id)}
                  style={{
                    borderWidth: 1,
                    borderColor: supplier.id === chosen ? colors.ink : colors.line,
                    backgroundColor: supplier.id === chosen ? colors.ink : colors.card,
                    borderRadius: 999,
                    paddingHorizontal: 12,
                    paddingVertical: 6,
                  }}
                >
                  <Text style={{ color: supplier.id === chosen ? colors.bg : colors.ink, fontWeight: "700" }}>
                    {supplier.name}
                  </Text>
                </Pressable>
              ))}
            </View>
          )}

          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Part or SKU"
            accessibilityLabel="Find a part to purchase"
            placeholderTextColor={colors.faint}
            autoCorrect={false}
            style={{
              backgroundColor: colors.card,
              borderColor: colors.line,
              borderWidth: 1,
              borderRadius: 14,
              padding: 14,
              marginBottom: 10,
              color: colors.ink,
            }}
          />
          {term.length >= 2 && !hits.loading && (hits.data ?? []).length === 0 ? (
            <Text style={{ color: colors.soft, marginBottom: 10 }}>No part matches "{term}".</Text>
          ) : null}
          {(hits.data ?? []).map((hit) => (
            <Pressable key={hit.variantId} onPress={() => addLine(hit)}>
              <Card>
                <Text style={{ fontWeight: "700", color: colors.ink }}>{hit.productName}</Text>
                <Text style={{ color: colors.soft, marginTop: 4 }}>
                  {hit.sku} · {money(hit.price)}
                </Text>
              </Card>
            </Pressable>
          ))}

          {lines.map((line) => (
            <Card key={line.variantId}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>{line.name}</Text>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 16, marginTop: 8 }}>
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
                <Text style={{ color: colors.ink }}>{line.quantity}</Text>
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
                  <Text style={{ color: colors.bad, fontWeight: "700" }}>Remove</Text>
                </Pressable>
              </View>
            </Card>
          ))}

          <PrimaryButton
            label={receive.busy ? "Receiving…" : `Receive ${money(total)}`}
            disabled={receive.busy || !chosen || lines.length === 0}
            onPress={() => void receive.run()}
          />

          {rows.length === 0 ? (
            <Empty title="No purchases yet" hint="Receive a supplier delivery and it lands here with what is still due." />
          ) : (
            rows.map((row) => (
              <Card key={row.id}>
                <Text style={{ fontWeight: "700", color: colors.ink }}>{row.supplierName ?? "Purchase"}</Text>
                <Text style={{ color: colors.soft, marginTop: 4 }}>
                  {row.status} · {money(row.total)} · due {money(row.outstanding)}
                </Text>
              </Card>
            ))
          )}
        </>
      )}
    </Screen>
  );
}
