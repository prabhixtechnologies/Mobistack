import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { useTheme } from "../lib/theme";

/**
 * First-load state. A spinner rather than a blank screen, because on a slow
 * counter connection a blank list is indistinguishable from an empty shop.
 */
export function Loading({ label = "Loading…" }: { label?: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.centre}>
      <ActivityIndicator color={colors.accent} />
      <Text style={{ color: colors.soft, marginTop: 12 }}>{label}</Text>
    </View>
  );
}

/**
 * Zero-row state. Always carries a hint: an unexplained empty panel reads as a
 * broken screen, and the shopkeeper's next move is to call support.
 */
export function Empty({ title, hint }: { title: string; hint?: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.centre}>
      <Text style={{ color: colors.ink, fontWeight: "700", fontSize: 16, textAlign: "center" }}>{title}</Text>
      {hint ? (
        <Text style={{ color: colors.soft, marginTop: 8, textAlign: "center", lineHeight: 20 }}>{hint}</Text>
      ) : null}
    </View>
  );
}

/** Failure state with a retry, so the screen is not a dead end. */
export function Failed({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { colors } = useTheme();
  return (
    <View style={styles.centre}>
      <Text style={{ color: colors.bad, fontWeight: "700", textAlign: "center" }}>That didn't load</Text>
      <Text style={{ color: colors.soft, marginTop: 8, textAlign: "center", lineHeight: 20 }}>{message}</Text>
      {onRetry ? (
        <Pressable onPress={onRetry} hitSlop={12} style={[styles.retry, { borderColor: colors.line }]}>
          <Text style={{ color: colors.ink, fontWeight: "700" }}>Try again</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/** Strip shown when the rows on screen came from the on-device copy. */
export function OfflineNotice({ what }: { what: string }) {
  const { colors } = useTheme();
  return (
    <View style={[styles.strip, { backgroundColor: colors.warnSoft }]}>
      <Text style={{ color: colors.warn, fontWeight: "600" }}>
        No connection — showing the {what} saved on this phone.
      </Text>
    </View>
  );
}

/** Inline problem message for a failed write. */
export function Problem({ message }: { message: string }) {
  const { colors } = useTheme();
  return (
    <View style={[styles.strip, { backgroundColor: colors.badSoft }]}>
      <Text style={{ color: colors.bad, fontWeight: "600" }}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  centre: { paddingVertical: 48, paddingHorizontal: 24, alignItems: "center" },
  strip: { borderRadius: 12, padding: 12, marginBottom: 12 },
  retry: {
    marginTop: 16,
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 18,
    minHeight: 44,
    justifyContent: "center",
  },
});
