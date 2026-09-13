import { useCallback, useState } from "react";
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedVariants, searchVariants, type CachedVariant } from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
import { useAction } from "../../lib/useAction";
import { useDebounced } from "../../lib/useDebounced";
import { useScreenData } from "../../lib/useScreenData";
import { BarcodeScanButton } from "../../components/BarcodeScan";
import { Empty, Failed, Loading, OfflineNotice, Problem } from "../../components/ListState";
import { money, stockColor, useTheme } from "../../lib/theme";

export default function InventoryScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [query, setQuery] = useState("");
  const settled = useDebounced(query);
  const [receiveFor, setReceiveFor] = useState<CachedVariant | null>(null);
  const [qty, setQty] = useState("1");

  const term = settled.trim();
  const load = useCallback(async () => {
    const search = term ? `q=${encodeURIComponent(term)}&` : "";
    const page = await api<{ content: CachedVariant[] }>(`/api/v1/variants?${search}size=40`);
    return page.content;
  }, [term]);
  const fallback = useCallback(async () => searchVariants(await cachedVariants(), term), [term]);
  const parts = useScreenData<CachedVariant[]>(term, load, { fallback });

  const receive = useAction(
    async (variant: CachedVariant, quantity: number) => {
      const payload = { variantId: variant.id, quantity, unitCost: 0, reason: "Counter receipt" };
      const key = `${Date.now()}-${variant.id}`;
      try {
        await api("/api/v1/inventory/receive", {
          method: "POST",
          headers: { "Idempotency-Key": key },
          body: JSON.stringify(payload),
        });
      } catch {
        // Queue it rather than lose it; the same key stops the retry doubling up.
        await enqueue({ type: "RECEIVE", idempotencyKey: key, receive: payload });
      }
      setReceiveFor(null);
      setQty("1");
      parts.refresh();
    },
    { fallbackError: "Could not add that stock." },
  );

  const rows = parts.data ?? [];

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <FlatList
        data={rows}
        keyExtractor={(row) => row.id}
        contentContainerStyle={styles.page}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl refreshing={parts.refreshing} onRefresh={parts.refresh} tintColor={colors.accent} />
        }
        ListHeaderComponent={
          <>
            <Text style={styles.title}>Inventory</Text>
            {parts.offline ? <OfflineNotice what="catalogue" /> : null}
            {receive.error ? <Problem message={receive.error} /> : null}
            <BarcodeScanButton onScan={setQuery} />
            <TextInput
              style={styles.search}
              placeholder="Part, SKU, barcode"
              placeholderTextColor={colors.faint}
              accessibilityLabel="Search inventory"
              value={query}
              onChangeText={setQuery}
              autoCorrect={false}
            />
          </>
        }
        ListEmptyComponent={
          parts.loading ? (
            <Loading label="Loading the shelf…" />
          ) : parts.error ? (
            <Failed message={parts.error} onRetry={parts.refresh} />
          ) : (
            <Empty
              title={term ? "Nothing matches that" : "No parts yet"}
              hint={
                term
                  ? "Try a shorter search, or scan the barcode on the box."
                  : "Receive a purchase on the web console and the shelf appears here."
              }
            />
          )
        }
        renderItem={({ item }) => {
          const tone = stockColor(item.stockStatus, colors);
          return (
            <View style={styles.card}>
              <View style={{ flex: 1 }}>
                <Text style={styles.name}>{item.productName}</Text>
                <Text style={styles.sub}>
                  {item.variantName} · {item.sku}
                </Text>
                <Text style={styles.price}>{money(item.retailPrice)}</Text>
              </View>
              <View style={{ alignItems: "flex-end", gap: 8 }}>
                <View style={[styles.badge, { backgroundColor: tone.bg }]}>
                  <Text style={{ color: tone.fg, fontWeight: "700" }}>{item.availableQty}</Text>
                </View>
                <Pressable onPress={() => setReceiveFor(item)} hitSlop={8}>
                  <Text style={{ fontWeight: "700", color: colors.ink }}>Add</Text>
                </Pressable>
              </View>
            </View>
          );
        }}
      />

      {receiveFor && (
        <View style={styles.sheet}>
          <Text style={styles.name}>Add stock · {receiveFor.productName}</Text>
          <TextInput
            style={styles.search}
            keyboardType="number-pad"
            value={qty}
            onChangeText={setQty}
            placeholderTextColor={colors.faint}
            accessibilityLabel="Quantity"
          />
          <Pressable
            style={[styles.btn, receive.busy && { opacity: 0.5 }]}
            disabled={receive.busy || !(Number(qty) > 0)}
            onPress={() => void receive.run(receiveFor, Number(qty))}
          >
            <Text style={styles.btnText}>{receive.busy ? "Saving…" : "Receive"}</Text>
          </Pressable>
          <Pressable onPress={() => setReceiveFor(null)} disabled={receive.busy}>
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
    title: { fontSize: 32, fontWeight: "600", marginBottom: 14, color: colors.ink },
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
    btn: { backgroundColor: colors.accent, borderRadius: 14, padding: 14, alignItems: "center" },
    btnText: { color: colors.accentInk, fontWeight: "700" },
  });
}
