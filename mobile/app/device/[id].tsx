import { useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { api } from "../../lib/api";
import { cachedDeviceView, saveDeviceView } from "../../lib/offline";
import { money, stockColor, useTheme } from "../../lib/theme";
import { Empty, Loading, OfflineNotice } from "../../components/ListState";
import { Screen } from "../../components/Screen";

interface ViewModel {
  device: { name: string; brandName: string; aliases: string[] };
  compatibleModels: { id: string; name: string; brandName: string }[];
  categories: {
    categoryId: string;
    categoryName: string;
    totalAvailable: number;
    stockStatus: string;
    minPrice?: number;
    maxPrice?: number;
    options: {
      variantId: string;
      variantName: string;
      availableQty: number;
      price: number;
      stockStatus: string;
    }[];
  }[];
  totalPartsAvailable: number;
}

export default function DeviceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useTheme();
  const [view, setView] = useState<ViewModel | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const styles = makeStyles(colors);

  useEffect(() => {
    if (!id) {
      return;
    }
    let cancelled = false;
    (async () => {
      const cached = await cachedDeviceView<ViewModel>(id);
      if (!cancelled && cached) {
        setView(cached);
      }
      try {
        const live = await api<ViewModel>(`/api/v1/devices/${id}/compatibility`);
        if (!cancelled) {
          setView(live);
          setOffline(false);
        }
        await saveDeviceView(id, live);
      } catch {
        if (!cancelled) {
          setOffline(true);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (!view) {
    return (
      <Screen
        title="Device"
        copy={
          offline
            ? "This device has not been opened on this phone yet. Connect once to keep its parts list offline."
            : "Looking up parts…"
        }
        back
      >
        {offline ? (
          <Empty
            title="Not on this phone yet"
            hint="Connect once to keep this phone's parts list offline."
          />
        ) : (
          <Loading label="Looking up parts…" />
        )}
      </Screen>
    );
  }

  return (
    <Screen
      title={`${view.device.brandName} ${view.device.name}`}
      copy={`${view.totalPartsAvailable} parts on the shelf`}
      back
    >
      {offline ? <OfflineNotice what="parts list" /> : null}
      <Text style={styles.section}>Compatible models</Text>
      <Text style={styles.body}>
        {[`${view.device.brandName} ${view.device.name}`, ...view.compatibleModels.map((m) => `${m.brandName} ${m.name}`)].join("  =  ")}
      </Text>
      {view.device.aliases.length > 0 && <Text style={styles.sub}>Also {view.device.aliases.join(", ")}</Text>}

      {view.categories.length === 0 ? (
        <Empty title="No parts linked yet" hint="Add a compatibility group on the product so this phone shows stock." />
      ) : (
        view.categories.map((category) => {
          const tone = stockColor(category.stockStatus, colors);
          return (
            <View key={category.categoryId} style={styles.card}>
              <Pressable
                hitSlop={12}
                style={{ minHeight: 44, justifyContent: "center" }}
                onPress={() => setOpen(open === category.categoryId ? null : category.categoryId)}
              >
                <View style={styles.row}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.name}>{category.categoryName}</Text>
                    <Text style={styles.sub}>
                      {category.totalAvailable} in stock
                      {category.minPrice != null ? ` · ${money(category.minPrice)}` : ""}
                      {category.maxPrice != null && category.maxPrice !== category.minPrice ? `–${money(category.maxPrice)}` : ""}
                    </Text>
                  </View>
                  <View style={[styles.badge, { backgroundColor: tone.bg }]}>
                    <Text style={{ color: tone.fg, fontWeight: "700" }}>{category.stockStatus}</Text>
                  </View>
                </View>
              </Pressable>
              {open === category.categoryId &&
                category.options.map((option) => (
                  <View key={option.variantId} style={styles.option}>
                    <Text style={styles.name}>{option.variantName}</Text>
                    <Text style={styles.sub}>
                      {option.availableQty} avail · {money(option.price)}
                    </Text>
                  </View>
                ))}
            </View>
          );
        })
      )}
    </Screen>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    section: { marginTop: 8, fontWeight: "700", marginBottom: 8, color: colors.ink },
    body: { fontSize: 16, lineHeight: 24, color: colors.ink },
    sub: { color: colors.soft, marginTop: 6 },
    card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginTop: 12 },
    row: { flexDirection: "row", alignItems: "center", gap: 12 },
    name: { fontWeight: "700", color: colors.ink },
    badge: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 },
    option: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.line },
  });
}
