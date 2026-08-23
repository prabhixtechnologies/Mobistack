import { useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { api } from "../../lib/api";
import { money, stockColor, useTheme } from "../../lib/theme";

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
  const styles = makeStyles(colors);

  useEffect(() => {
    api<ViewModel>(`/api/v1/devices/${id}/compatibility`).then(setView);
  }, [id]);

  if (!view) {
    return (
      <View style={[styles.page, { backgroundColor: colors.bg, flex: 1 }]}>
        <Text style={styles.sub}>Looking up parts…</Text>
      </View>
    );
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>
        {view.device.brandName} {view.device.name}
      </Text>
      <Text style={styles.sub}>{view.totalPartsAvailable} parts on the shelf</Text>
      <Text style={styles.section}>Compatible models</Text>
      <Text style={styles.body}>
        {[`${view.device.brandName} ${view.device.name}`, ...view.compatibleModels.map((m) => `${m.brandName} ${m.name}`)].join("  =  ")}
      </Text>
      {view.device.aliases.length > 0 && <Text style={styles.sub}>Also {view.device.aliases.join(", ")}</Text>}

      {view.categories.map((category) => {
        const tone = stockColor(category.stockStatus, colors);
        return (
          <View key={category.categoryId} style={styles.card}>
            <Pressable onPress={() => setOpen(open === category.categoryId ? null : category.categoryId)}>
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
      })}
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 40 },
    title: { fontSize: 32, fontWeight: "500", letterSpacing: -0.7, color: colors.ink },
    section: { marginTop: 22, fontWeight: "700", marginBottom: 8, color: colors.ink },
    body: { fontSize: 16, lineHeight: 24, color: colors.ink },
    sub: { color: colors.soft, marginTop: 6 },
    card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginTop: 12 },
    row: { flexDirection: "row", alignItems: "center", gap: 12 },
    name: { fontWeight: "700", color: colors.ink },
    badge: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 },
    option: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.line },
  });
}
