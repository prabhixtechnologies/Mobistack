import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedVariants, searchVariants, type CachedVariant } from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
import { BarcodeScanButton } from "../../components/BarcodeScan";
import { money, stockColor, useTheme } from "../../lib/theme";

export default function InventoryScreen() {
  const { colors } = useTheme();
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<CachedVariant[]>([]);
  const [receive, setReceive] = useState<CachedVariant | null>(null);
  const [qty, setQty] = useState("1");
  const [offline, setOffline] = useState(false);
  const styles = makeStyles(colors);

  async function load(q = query) {
    try {
      const search = q ? `q=${encodeURIComponent(q)}&` : "";
      const page = await api<{ content: CachedVariant[] }>(`/api/v1/variants?${search}size=40`);
      setRows(page.content);
      setOffline(false);
    } catch {
      setRows(searchVariants(await cachedVariants(), q));
      setOffline(true);
    }
  }

  useEffect(() => {
    void load("");
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={styles.page}>
        <Text style={styles.title}>Inventory</Text>
        {offline ? <Text style={styles.banner}>Showing the on-device catalogue.</Text> : null}
        <BarcodeScanButton
          onScan={(value) => {
            setQuery(value);
            void load(value);
          }}
        />
        <TextInput
          style={styles.search}
          placeholder="Part, SKU, barcode"
          placeholderTextColor={colors.faint}
          value={query}
          onChangeText={(value) => {
            setQuery(value);
            void load(value);
          }}
        />
        {rows.map((row) => {
          const tone = stockColor(row.stockStatus, colors);
          return (
            <View key={row.id} style={styles.card}>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>{row.productName}</Text>
                <Text style={styles.sub}>
                  {row.variantName} · {row.sku}
                </Text>
                <Text style={styles.price}>{money(row.retailPrice)}</Text>
              </View>
              <View style={{ alignItems: "flex-end", gap: 8 }}>
                <View style={[styles.badge, { backgroundColor: tone.bg }]}>
                  <Text style={{ color: tone.fg, fontWeight: "700" }}>{row.availableQty}</Text>
                </View>
                <Pressable onPress={() => setReceive(row)}>
                  <Text style={{ fontWeight: "700", color: colors.ink }}>Add</Text>
                </Pressable>
              </View>
            </View>
          );
        })}
      </ScrollView>

      {receive && (
        <View style={styles.sheet}>
          <Text style={styles.name}>Add stock · {receive.productName}</Text>
          <TextInput
            style={styles.search}
            keyboardType="number-pad"
            value={qty}
            onChangeText={setQty}
            placeholderTextColor={colors.faint}
          />
          <Pressable
            style={styles.btn}
            onPress={async () => {
              const payload = {
                variantId: receive.id,
                quantity: Number(qty),
                unitCost: 0,
                reason: "Counter receipt",
              };
              try {
                await api("/api/v1/inventory/receive", {
                  method: "POST",
                  body: JSON.stringify(payload),
                });
              } catch {
                await enqueue({
                  type: "RECEIVE",
                  idempotencyKey: `${Date.now()}-${receive.id}`,
                  receive: payload,
                });
              }
              setReceive(null);
              await load();
            }}
          >
            <Text style={styles.btnText}>Receive</Text>
          </Pressable>
          <Pressable onPress={() => setReceive(null)}>
            <Text style={{ textAlign: "center", marginTop: 12, color: colors.soft }}>Cancel</Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 40 },
    title: { fontSize: 32, fontWeight: "500", marginBottom: 14, color: colors.ink },
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
    card: {
      backgroundColor: colors.card,
      borderRadius: 16,
      padding: 14,
      marginBottom: 10,
      flexDirection: "row",
      gap: 12,
    },
    name: { fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 4 },
    price: { marginTop: 8, fontWeight: "600", color: colors.ink },
    badge: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 },
    sheet: {
      position: "absolute",
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: colors.card,
      padding: 22,
      borderTopLeftRadius: 24,
      borderTopRightRadius: 24,
    },
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center" },
    btnText: { color: colors.bg, fontWeight: "700" },
  });
}
