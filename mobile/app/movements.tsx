import { useCallback } from "react";
import { Text } from "react-native";
import { Card, Screen } from "../components/Screen";
import { Empty, Failed, Loading } from "../components/ListState";
import { api } from "../lib/api";
import { useScreenData } from "../lib/useScreenData";
import { useTheme } from "../lib/theme";

interface Movement {
  id: string;
  type: string;
  quantity: number;
  balanceAfter: number;
  referenceLabel?: string;
  reason?: string;
  occurredAt?: string;
}

export default function MovementsScreen() {
  const { colors } = useTheme();
  const load = useCallback(async () => {
    const page = await api<{ content: Movement[] }>("/api/v1/inventory/transactions?size=40");
    return page.content;
  }, []);
  const movements = useScreenData<Movement[]>("movements", load);
  const rows = movements.data ?? [];

  return (
    <Screen
      title="Movements"
      copy="Stock in and out of this shop."
      back
      onRefresh={movements.refresh}
      refreshing={movements.refreshing}
    >
      {movements.loading && rows.length === 0 ? (
        <Loading label="Loading the ledger…" />
      ) : movements.error ? (
        <Failed message={movements.error} onRetry={movements.refresh} />
      ) : rows.length === 0 ? (
        <Empty title="No movements yet" hint="Receiving, selling, or fitting a part to a repair all show up here." />
      ) : (
        rows.map((row) => (
          <Card key={row.id}>
            <Text style={{ fontWeight: "700", color: colors.ink }}>
              {row.type} · {row.quantity}
            </Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              {row.referenceLabel ?? row.reason ?? "Stock"} · after {row.balanceAfter}
            </Text>
          </Card>
        ))
      )}
    </Screen>
  );
}
