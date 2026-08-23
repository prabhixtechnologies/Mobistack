import { Redirect } from "expo-router";
import { ActivityIndicator, View } from "react-native";
import { useAuth } from "../lib/auth";
import { selectedWorkspaceId } from "../lib/api";
import { useTheme } from "../lib/theme";

export default function Index() {
  const { user, ready } = useAuth();
  const { colors } = useTheme();
  if (!ready) {
    return (
      <View style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }}>
        <ActivityIndicator color={colors.ink} />
      </View>
    );
  }
  if (!user) {
    return <Redirect href="/login" />;
  }
  return <Redirect href={selectedWorkspaceId(user) ? "/(tabs)/home" : "/workspaces"} />;
}
