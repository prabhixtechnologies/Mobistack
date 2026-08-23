import { useEffect, useState } from "react";
import { Text } from "react-native";
import { Card, Screen } from "../components/Screen";
import { api } from "../lib/api";
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
  const [rows, setRows] = useState<Movement[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<{ content: Movement[] }>("/api/v1/inventory/transactions?size=40")
      .then((page) => setRows(page.content))
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <Screen title="Movements" copy="Stock in and out of this shop." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      {rows.map((row) => (
        <Card key={row.id}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>
            {row.type} · {row.quantity}
          </Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {row.referenceLabel ?? row.reason ?? "Stock"} · after {row.balanceAfter}
          </Text>
        </Card>
      ))}
    </Screen>
  );
}
