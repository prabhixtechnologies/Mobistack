import { useEffect, useState } from "react";
import { Pressable, Text, TextInput, View } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { api } from "../lib/api";
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

export default function PurchasesScreen() {
  const { colors } = useTheme();
  const [rows, setRows] = useState<Purchase[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [supplierId, setSupplierId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<Hit[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [page, party] = await Promise.all([
      api<{ content: Purchase[] }>("/api/v1/purchases?size=20"),
      api<{ content: Supplier[] }>("/api/v1/suppliers?size=40"),
    ]);
    setRows(page.content);
    setSuppliers(party.content);
    if (!supplierId && party.content[0]) {
      setSupplierId(party.content[0].id);
    }
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      return;
    }
    const handle = setTimeout(() => {
      api<{ parts: Hit[] }>(`/api/v1/search?q=${encodeURIComponent(query)}`)
        .then((result) => setHits(result.parts ?? []))
        .catch(() => setHits([]));
    }, 160);
    return () => clearTimeout(handle);
  }, [query]);

  const total = lines.reduce((sum, line) => sum + line.unitCost * line.quantity, 0);

  return (
    <Screen title="Purchases" copy="Receive supplier stock onto the same ledger the counter sells from." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
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
                borderColor: supplier.id === supplierId ? colors.ink : colors.line,
                backgroundColor: supplier.id === supplierId ? colors.ink : colors.card,
                borderRadius: 999,
                paddingHorizontal: 12,
                paddingVertical: 6,
              }}
            >
              <Text style={{ color: supplier.id === supplierId ? colors.bg : colors.ink, fontWeight: "700" }}>
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
        placeholderTextColor={colors.faint}
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
      {hits.map((hit) => (
        <Pressable
          key={hit.variantId}
          onPress={() => {
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
            setHits([]);
          }}
        >
          <Card>
            <Text style={{ fontWeight: "700", color: colors.ink }}>{hit.productName}</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>{hit.sku} · {money(hit.price)}</Text>
          </Card>
        </Pressable>
      ))}
      {lines.map((line) => (
        <Card key={line.variantId}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{line.name}</Text>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 12, marginTop: 8 }}>
            <Pressable onPress={() => setLines((current) => current.map((row) => row.variantId === line.variantId ? { ...row, quantity: Math.max(1, row.quantity - 1) } : row))}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>−</Text>
            </Pressable>
            <Text style={{ color: colors.ink }}>{line.quantity}</Text>
            <Pressable onPress={() => setLines((current) => current.map((row) => row.variantId === line.variantId ? { ...row, quantity: row.quantity + 1 } : row))}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>+</Text>
            </Pressable>
            <Pressable onPress={() => setLines((current) => current.filter((row) => row.variantId !== line.variantId))}>
              <Text style={{ color: colors.bad, fontWeight: "700" }}>Remove</Text>
            </Pressable>
          </View>
        </Card>
      ))}
      <PrimaryButton
        label={`Receive ${money(total)}`}
        disabled={!supplierId || lines.length === 0}
        onPress={() => {
          void api("/api/v1/purchases", {
            method: "POST",
            body: JSON.stringify({
              supplierId,
              tax: 0,
              idempotencyKey: `${Date.now()}-${Math.random()}`,
              items: lines.map((line) => ({
                variantId: line.variantId,
                quantity: line.quantity,
                unitCost: line.unitCost,
              })),
              payments: [{ method: "CASH", amount: total }],
            }),
          })
            .then(() => {
              setLines([]);
              return load();
            })
            .catch((err: Error) => setError(err.message));
        }}
      />
      {rows.map((row) => (
        <Card key={row.id}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{row.supplierName ?? "Purchase"}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {row.status} · {money(row.total)} · due {money(row.outstanding)}
          </Text>
        </Card>
      ))}
    </Screen>
  );
}
