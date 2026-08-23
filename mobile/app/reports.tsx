import { useEffect, useState } from "react";
import { Text } from "react-native";
import { Card, Screen } from "../components/Screen";
import { api } from "../lib/api";
import { money, useTheme } from "../lib/theme";

interface Bundle {
  sales: { sales: number; profit: number; transactions: number };
  purchases: { sales: number; profit: number; transactions: number };
  repairRevenue: number;
  repairs: { received: number; diagnosing: number; inRepair: number; ready: number; delivered: number; pending: number };
  deadStock: { variantId: string; name: string; stock: number; stockValue: number; suggestion?: string }[];
}

export default function ReportsScreen() {
  const { colors } = useTheme();
  const [bundle, setBundle] = useState<Bundle | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Bundle>("/api/v1/reports?range=THIS_MONTH")
      .then(setBundle)
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <Screen title="Reports" copy="This month on this shop." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      <Card>
        <Text style={{ fontWeight: "700", color: colors.ink }}>Sales</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>
          {money(bundle?.sales.sales ?? 0)} · profit {money(bundle?.sales.profit ?? 0)} · {bundle?.sales.transactions ?? 0} bills
        </Text>
      </Card>
      <Card>
        <Text style={{ fontWeight: "700", color: colors.ink }}>Purchases</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>
          {money(bundle?.purchases.sales ?? 0)} · {bundle?.purchases.transactions ?? 0} receipts
        </Text>
      </Card>
      <Card>
        <Text style={{ fontWeight: "700", color: colors.ink }}>Repairs</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>
          Revenue {money(bundle?.repairRevenue ?? 0)} · pending {bundle?.repairs.pending ?? 0}
        </Text>
      </Card>
      {(bundle?.deadStock ?? []).map((row) => (
        <Card key={row.variantId}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{row.name}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {row.stock} on hand · {money(row.stockValue)} · {row.suggestion ?? "Quiet stock"}
          </Text>
        </Card>
      ))}
    </Screen>
  );
}
