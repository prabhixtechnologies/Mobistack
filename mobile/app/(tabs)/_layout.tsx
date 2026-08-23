import { Tabs } from "expo-router";
import { Text } from "react-native";
import { useTheme } from "../../lib/theme";

export default function TabsLayout() {
  const { colors } = useTheme();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.ink,
        tabBarInactiveTintColor: colors.faint,
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.line },
      }}
    >
      <Tabs.Screen name="home" options={{ title: "Home", tabBarIcon: () => <Text>⌂</Text> }} />
      <Tabs.Screen name="inventory" options={{ title: "Inventory", tabBarIcon: () => <Text>□</Text> }} />
      <Tabs.Screen name="sales" options={{ title: "Sales", tabBarIcon: () => <Text>₹</Text> }} />
      <Tabs.Screen name="repairs" options={{ title: "Repairs", tabBarIcon: () => <Text>⚙</Text> }} />
      <Tabs.Screen name="more" options={{ title: "More", tabBarIcon: () => <Text>⋯</Text> }} />
    </Tabs>
  );
}
