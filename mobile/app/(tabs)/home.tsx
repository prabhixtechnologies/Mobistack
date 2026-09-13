import { useCallback, useEffect, useState } from "react";
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import {
  cachedDashboard,
  cachedDevices,
  cachedVariants,
  searchDevices,
  searchVariants,
  syncNow,
  type CachedDashboard,
  type CachedVariant,
} from "../../lib/offline";
import { CompatibilityHub } from "../../components/CompatibilityHub";
import { useDebounced } from "../../lib/useDebounced";
import { useScreenData } from "../../lib/useScreenData";
import { Loading, OfflineNotice } from "../../components/ListState";
import { money, useTheme } from "../../lib/theme";

interface SearchHit {
  devices: { id: string; name: string; brandName: string; matchedAliases: string[] }[];
}

/** The greeting was fixed at "Good evening", which read as broken all morning. */
function greetingFor(date = new Date()): string {
  const hour = date.getHours();
  if (hour < 12) {
    return "Good morning";
  }
  return hour < 17 ? "Good afternoon" : "Good evening";
}

export default function HomeScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [query, setQuery] = useState("");
  const settled = useDebounced(query);
  const [hits, setHits] = useState<SearchHit["devices"]>([]);
  const [parts, setParts] = useState<CachedVariant[]>([]);
  const [fabOpen, setFabOpen] = useState(false);

  const firstName = user?.fullName?.split(" ")[0] ?? "there";
  const greeting = `${greetingFor()}, ${firstName}.`;
  const catalogOnly = Boolean(user?.catalogOnly);
  const locked = Boolean(user?.paymentRequired);

  const loadDash = useCallback(async () => {
    const live = await api<CachedDashboard>("/api/v1/dashboard");
    // Piggybacked on the dashboard load: opening the app is the moment a phone
    // is most likely to have signal after a spell on the counter without any.
    void syncNow();
    return live;
  }, []);
  const dashboard = useScreenData<CachedDashboard | null>(
    `dash:${user?.workspaceId ?? user?.shopId ?? "none"}`,
    loadDash,
    { fallback: cachedDashboard, enabled: !catalogOnly },
  );
  const dash = dashboard.data;

  useEffect(() => {
    const term = settled.trim();
    if (term.length < 2) {
      setHits([]);
      setParts([]);
      return;
    }
    let live = true;
    api<SearchHit>(`/api/v1/search?q=${encodeURIComponent(term)}`)
      .then((res) => {
        if (!live) {
          return;
        }
        setHits(res.devices);
        setParts([]);
      })
      .catch(async () => {
        // No signal: search what is on the phone instead of showing nothing.
        const devices = searchDevices(await cachedDevices(), term).slice(0, 8);
        if (!live) {
          return;
        }
        setHits(
          devices.map((device) => ({
            id: device.id,
            name: device.name,
            brandName: device.brandName,
            matchedAliases: (device.aliases ?? []).map((alias) => alias.alias),
          })),
        );
        setParts(searchVariants(await cachedVariants(), term).slice(0, 12));
      });
    return () => {
      live = false;
    };
  }, [settled]);

  if (catalogOnly) {
    return <CompatibilityHub greeting={greeting} />;
  }

  const searching = settled.trim().length >= 2;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView
        contentContainerStyle={styles.page}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl
            refreshing={dashboard.refreshing}
            onRefresh={dashboard.refresh}
            tintColor={colors.accent}
          />
        }
      >
        <Text style={styles.hello}>{greeting}</Text>
        {locked ? (
          <Pressable onPress={() => router.push("/billing")}>
            <Text style={styles.banner}>
              Payment is pending. Tap here to activate this shop — sales, repairs, and stock stay locked until then.
            </Text>
          </Pressable>
        ) : null}
        {dashboard.offline ? <OfflineNotice what="figures" /> : null}

        <Pressable style={styles.hit} onPress={() => router.push("/compatibility")}>
          <Text style={styles.hitTitle}>Universal lists</Text>
          <Text style={styles.hitSub}>Tempered glass, OCA, displays, and the rest</Text>
        </Pressable>
        <TextInput
          style={styles.search}
          placeholder="Search Realme 6…"
          placeholderTextColor={colors.faint}
          accessibilityLabel="Search devices"
          value={query}
          onChangeText={setQuery}
          autoCorrect={false}
        />
        {searching && hits.length === 0 && parts.length === 0 ? (
          <Text style={styles.hitSub}>Nothing matches “{settled.trim()}”.</Text>
        ) : null}
        {hits.map((device) => (
          <Pressable key={device.id} style={styles.hit} onPress={() => router.push(`/device/${device.id}`)}>
            <Text style={styles.hitTitle}>
              {device.brandName} {device.name}
            </Text>
            <Text style={styles.hitSub}>
              {device.matchedAliases.length ? device.matchedAliases.join(", ") : "Open compatibility"}
            </Text>
          </Pressable>
        ))}
        {parts.map((part) => (
          <View key={part.id} style={styles.hit}>
            <Text style={styles.hitTitle}>{part.productName}</Text>
            <Text style={styles.hitSub}>
              {part.sku} · {part.availableQty} · {money(part.retailPrice)}
            </Text>
          </View>
        ))}

        {dashboard.loading && !dash ? (
          <Loading label="Reading today’s figures…" />
        ) : (
          <>
            <View style={styles.grid}>
              <Tile label="Today’s sales" value={money(dash?.sales.todaySales ?? 0)} colors={colors} />
              <Tile label="Profit" value={money(dash?.sales.todayProfit ?? 0)} colors={colors} />
              <Tile label="Stock value" value={money(dash?.inventory.stockValueAtCost ?? 0)} colors={colors} />
              <Tile
                label="Low / out"
                value={`${dash?.inventory.lowStockCount ?? 0} / ${dash?.inventory.outOfStockCount ?? 0}`}
                colors={colors}
              />
            </View>

            <Text style={styles.section}>Alerts</Text>
            {(dash?.alerts ?? []).map((alert) => (
              <View key={alert.id} style={styles.card}>
                <Text style={styles.hitTitle}>{alert.productName}</Text>
                <Text style={styles.hitSub}>{alert.message}</Text>
              </View>
            ))}
            {dash && dash.alerts.length === 0 ? (
              <Text style={styles.hitSub}>No stock emergencies right now.</Text>
            ) : null}
            {!dash && dashboard.error ? <Text style={styles.hitSub}>{dashboard.error}</Text> : null}
          </>
        )}
      </ScrollView>

      <Pressable style={styles.fab} onPress={() => setFabOpen(!fabOpen)}>
        <Text style={styles.fabPlus}>{fabOpen ? "×" : "+"}</Text>
      </Pressable>
      {fabOpen && (
        <View style={styles.fabMenu}>
          {/* An unpaid shop cannot record anything, so offering these shortcuts
              only leads to a rejection at the end of the form. */}
          {locked ? (
            <FabAction label="Activate this shop" onPress={() => router.push("/billing")} colors={colors} />
          ) : (
            <>
              <FabAction label="Add stock" onPress={() => router.push("/inventory")} colors={colors} />
              <FabAction label="New sale" onPress={() => router.push("/(tabs)/sales")} colors={colors} />
              <FabAction label="New repair" onPress={() => router.push("/(tabs)/repairs")} colors={colors} />
              <FabAction label="Receive purchase" onPress={() => router.push("/purchases")} colors={colors} />
            </>
          )}
        </View>
      )}
    </View>
  );
}

function Tile({ label, value, colors }: { label: string; value: string; colors: ReturnType<typeof useTheme>["colors"] }) {
  return (
      <View style={{ width: "48%", backgroundColor: colors.card, borderRadius: 16, padding: 16 }}>
      <Text style={{ color: colors.faint, fontSize: 12, fontWeight: "600" }}>{label}</Text>
      <Text style={{ fontSize: 22, fontWeight: "700", marginTop: 8, letterSpacing: -0.6, color: colors.ink }}>{value}</Text>
    </View>
  );
}

function FabAction({ label, onPress, colors }: { label: string; onPress: () => void; colors: ReturnType<typeof useTheme>["colors"] }) {
  return (
    <Pressable style={{ backgroundColor: colors.card, paddingHorizontal: 16, paddingVertical: 12, borderRadius: 14 }} onPress={onPress}>
      <Text style={{ fontWeight: "600", color: colors.ink }}>{label}</Text>
    </Pressable>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 120 },
    hello: { fontSize: 32, fontWeight: "600", letterSpacing: -0.8, marginBottom: 16, color: colors.ink },
    banner: { color: colors.warn, marginBottom: 12, fontWeight: "600" },
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 18,
      padding: 14,
      marginBottom: 12,
      color: colors.ink,
    },
    hit: { backgroundColor: colors.card, borderRadius: 14, padding: 14, marginBottom: 8 },
    hitTitle: { fontWeight: "700", color: colors.ink },
    hitSub: { color: colors.soft, marginTop: 4 },
    grid: { flexDirection: "row", flexWrap: "wrap", gap: 10, marginTop: 18 },
    section: { marginTop: 22, marginBottom: 8, fontSize: 18, fontWeight: "700", color: colors.ink },
    card: { backgroundColor: colors.card, borderRadius: 14, padding: 14, marginBottom: 8 },
    fab: {
      position: "absolute",
      right: 20,
      bottom: 24,
      width: 58,
      height: 58,
      borderRadius: 29,
      backgroundColor: colors.accent,
      alignItems: "center",
      justifyContent: "center",
    },
    fabPlus: { color: colors.accentInk, fontSize: 30, lineHeight: 32 },
    fabMenu: { position: "absolute", right: 20, bottom: 92, gap: 8 },
  });
}
