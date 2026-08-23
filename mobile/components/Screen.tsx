import { type ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { router } from "expo-router";
import { useTheme } from "../lib/theme";

export function Screen({
  title,
  copy,
  children,
  back,
}: {
  title: string;
  copy?: string;
  children: ReactNode;
  back?: boolean;
}) {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      {back ? (
        <Pressable onPress={() => router.back()} style={{ marginBottom: 8 }}>
          <Text style={{ color: colors.soft, fontWeight: "700" }}>Back</Text>
        </Pressable>
      ) : null}
      <Text style={styles.title}>{title}</Text>
      {copy ? <Text style={styles.copy}>{copy}</Text> : null}
      {children}
    </ScrollView>
  );
}

export function Card({ children }: { children: ReactNode }) {
  const { colors } = useTheme();
  return (
    <View
      style={{
        backgroundColor: colors.card,
        borderRadius: 16,
        padding: 14,
        marginBottom: 10,
      }}
    >
      {children}
    </View>
  );
}

export function PrimaryButton({
  label,
  onPress,
  disabled,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  const { colors } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={{
        backgroundColor: colors.ink,
        borderRadius: 14,
        padding: 14,
        alignItems: "center",
        marginBottom: 12,
        opacity: disabled ? 0.5 : 1,
      }}
    >
      <Text style={{ color: colors.bg, fontWeight: "700" }}>{label}</Text>
    </Pressable>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 48 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink, marginBottom: 8 },
    copy: { color: colors.soft, marginBottom: 16, lineHeight: 22 },
  });
}
