import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { router } from "expo-router";
import { api } from "../../lib/api";
import { useAuth } from "../../lib/auth";
import { cachedDashboard, cachedDevices, cachedVariants, searchDevices, searchVariants, syncNow, type CachedDashboard, type CachedVariant } from "../../lib/offline";
import { money, useTheme } from "../../lib/theme";

interface SearchHit {
  devices: { id: string; name: string; brandName: string; matchedAliases: string[] }[];
}

export default function HomeScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const [dash, setDash] = useState<CachedDashboard | null>(null);
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<SearchHit["devices"]>([]);
  const [parts, setParts] = useState<CachedVariant[]>([]);
  const [fabOpen, setFabOpen] = useState(false);
  const [offline, setOffline] = useState(false);
  const styles = makeStyles(colors);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const cached = await cachedDashboard();
      if (!cancelled && cached) {
        setDash(cached);
      }
      try {
        if (user?.catalogOnly) {
          return;
        }
        const live = await api<CachedDashboard>("/api/v1/dashboard");
        if (!cancelled) {
          setDash(live);
          setOffline(false);
        }
        await syncNow();
      } catch {
        if (!cancelled) {
          setOffline(true);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [user?.workspaceId, user?.shopId, user?.catalogOnly]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      setParts([]);
      return;
    }
    const handle = setTimeout(() => {
      api<SearchHit>(`/api/v1/search?q=${encodeURIComponent(query)}`)
        .then((res) => {
          setHits(res.devices);
          setParts([]);
        })
        .catch(async () => {
          const devices = searchDevices(await cachedDevices(), query).slice(0, 8);
          setHits(
            devices.map((device) => ({
              id: device.id,
              name: device.name,
              brandName: device.brandName,
              matchedAliases: (device.aliases ?? []).map((alias) => alias.alias),
            })),
          );
          setParts(searchVariants(await cachedVariants(), query).slice(0, 12));
        });
    }, 150);
    return () => clearTimeout(handle);
  }, [query]);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={styles.page}>
        <Text style={styles.hello}>Good evening, {user?.fullName.split(" ")[0]}.</Text>
        {user?.paymentRequired ? (
          <Text style={styles.banner}>
            Payment is pending. Open Billing in More to activate this shop. Sales, repairs, and stock stay locked until then.
          </Text>
        ) : null}
        {user?.catalogOnly ? (
          <Text style={styles.banner}>
            Compatibility plan. Search a phone to see parts that fit. The full shop unlocks on the monthly plan.
          </Text>
        ) : null}
        {offline ? <Text style={styles.banner}>Working offline from the last snapshot.</Text> : null}
        <TextInput
          style={styles.search}
          placeholder="Search Realme 6…"
          placeholderTextColor={colors.faint}
          value={query}
          onChangeText={setQuery}
        />
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

        {!user?.catalogOnly && (
        <View style={styles.grid}>
          <Tile label="Today’s sales" value={money(dash?.sales.todaySales ?? 0)} colors={colors} />
          <Tile label="Profit" value={money(dash?.sales.todayProfit ?? 0)} colors={colors} />
          <Tile label="Stock value" value={money(dash?.inventory.stockValueAtCost ?? 0)} colors={colors} />
          <Tile label="Low / out" value={`${dash?.inventory.lowStockCount ?? 0} / ${dash?.inventory.outOfStockCount ?? 0}`} colors={colors} />
        </View>
        )}

        {!user?.catalogOnly && (
          <>
            <Text style={styles.section}>Alerts</Text>
            {(dash?.alerts ?? []).map((alert) => (
              <View key={alert.id} style={styles.card}>
                <Text style={styles.hitTitle}>{alert.productName}</Text>
                <Text style={styles.hitSub}>{alert.message}</Text>
              </View>
            ))}
            {dash && dash.alerts.length === 0 && <Text style={styles.hitSub}>No stock emergencies this morning.</Text>}
          </>
        )}
      </ScrollView>

      {!user?.catalogOnly && (
        <>
          <Pressable style={styles.fab} onPress={() => setFabOpen(!fabOpen)}>
            <Text style={styles.fabPlus}>{fabOpen ? "×" : "+"}</Text>
          </Pressable>
          {fabOpen && (
            <View style={styles.fabMenu}>
              <FabAction label="Add stock" onPress={() => router.push("/inventory")} colors={colors} />
              <FabAction label="New sale" onPress={() => router.push("/(tabs)/sales")} colors={colors} />
              <FabAction label="New repair" onPress={() => router.push("/(tabs)/repairs")} colors={colors} />
              <FabAction label="Receive purchase" onPress={() => router.push("/purchases")} colors={colors} />
              {user?.paymentRequired ? (
                <FabAction label="Billing" onPress={() => router.push("/billing")} colors={colors} />
              ) : null}
            </View>
          )}
        </>
      )}
    </View>
  );
}

function Tile({ label, value, colors }: { label: string; value: string; colors: ReturnType<typeof useTheme>["colors"] }) {
  return (
    <View style={{ width: "48%", backgroundColor: colors.card, borderRadius: 16, padding: 14 }}>
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
    hello: { fontSize: 32, fontWeight: "500", letterSpacing: -0.8, marginBottom: 16, color: colors.ink },
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
      backgroundColor: colors.ink,
      alignItems: "center",
      justifyContent: "center",
    },
    fabPlus: { color: colors.bg, fontSize: 30, lineHeight: 32 },
    fabMenu: { position: "absolute", right: 20, bottom: 92, gap: 8 },
  });
}
