import { Tabs } from "expo-router";
import { Text } from "react-native";
import { useAuth } from "../../lib/auth";
import { hasFeature } from "../../lib/plan";
import { useTheme } from "../../lib/theme";

export default function TabsLayout() {
  const { colors } = useTheme();
  const { user } = useAuth();
  const catalogOnly = Boolean(user?.catalogOnly) || (hasFeature(user, "COMPATIBILITY") && !hasFeature(user, "SALES"));
  const showSales = hasFeature(user, "SALES") || (!user?.catalogOnly && (user?.features?.length ?? 0) === 0 && !user?.paymentRequired);
  const showInventory = hasFeature(user, "INVENTORY") || showSales;
  const showRepairs = hasFeature(user, "REPAIRS") || showSales;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.soft,
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
