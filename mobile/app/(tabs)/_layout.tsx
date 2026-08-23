import { Tabs } from "expo-router";
import { Text } from "react-native";
import { useAuth } from "../../lib/auth";
import { useTheme } from "../../lib/theme";

export default function TabsLayout() {
  const { colors } = useTheme();
  const { user } = useAuth();
  const features = user?.features ?? [];
  const catalogOnly = Boolean(user?.catalogOnly) || (features.includes("COMPATIBILITY") && !features.includes("SALES"));
  const showSales = features.includes("SALES") || (!user?.catalogOnly && features.length === 0 && !user?.paymentRequired);
  const showInventory = features.includes("INVENTORY") || showSales;
  const showRepairs = features.includes("REPAIRS") || showSales;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.ink,
        tabBarInactiveTintColor: colors.faint,
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.line },
      }}
    >
      <Tabs.Screen name="home" options={{ title: catalogOnly ? "Compatibility" : "Home", tabBarIcon: () => <Text>⌂</Text> }} />
      <Tabs.Screen name="inventory" options={{ title: "Inventory", href: showInventory ? undefined : null, tabBarIcon: () => <Text>□</Text> }} />
      <Tabs.Screen name="sales" options={{ title: "Sales", href: showSales ? undefined : null, tabBarIcon: () => <Text>₹</Text> }} />
      <Tabs.Screen name="repairs" options={{ title: "Repairs", href: showRepairs ? undefined : null, tabBarIcon: () => <Text>⚙</Text> }} />
      <Tabs.Screen name="more" options={{ title: "More", tabBarIcon: () => <Text>⋯</Text> }} />
    </Tabs>
  );
}
