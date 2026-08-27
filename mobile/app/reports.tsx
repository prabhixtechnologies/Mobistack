import { useCallback } from "react";
import { Text } from "react-native";
import { Card, Screen } from "../components/Screen";
import { Empty, Failed, Loading } from "../components/ListState";
import { api } from "../lib/api";
import { useScreenData } from "../lib/useScreenData";
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
  const load = useCallback(() => api<Bundle>("/api/v1/reports?range=THIS_MONTH"), []);
  const report = useScreenData<Bundle>("reports", load);
  const bundle = report.data;

  return (
    <Screen
      title="Reports"
      copy="This month on this shop."
      back
      onRefresh={report.refresh}
      refreshing={report.refreshing}
    >
      {report.loading && !bundle ? (
        <Loading label="Adding up the month…" />
      ) : report.error && !bundle ? (
        <Failed message={report.error} onRetry={report.refresh} />
      ) : !bundle ? (
        <Empty title="Nothing to report yet" hint="Take a sale or open a repair and the month starts filling in." />
      ) : (
        <>
          <Card>
            <Text style={{ fontWeight: "700", color: colors.ink }}>Sales</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              {money(bundle.sales.sales)} · profit {money(bundle.sales.profit)} · {bundle.sales.transactions} bills
            </Text>
          </Card>
          <Card>
            <Text style={{ fontWeight: "700", color: colors.ink }}>Purchases</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              {money(bundle.purchases.sales)} · {bundle.purchases.transactions} receipts
            </Text>
          </Card>
          <Card>
            <Text style={{ fontWeight: "700", color: colors.ink }}>Repairs</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              Revenue {money(bundle.repairRevenue)} · pending {bundle.repairs.pending}
            </Text>
          </Card>
          {bundle.deadStock.length > 0 ? (
            <Text style={{ color: colors.faint, fontWeight: "700", marginTop: 10, marginBottom: 8 }}>
              Quiet stock
            </Text>
          ) : null}
          {bundle.deadStock.map((row) => (
            <Card key={row.variantId}>
              <Text style={{ fontWeight: "700", color: colors.ink }}>{row.name}</Text>
              <Text style={{ color: colors.soft, marginTop: 4 }}>
                {row.stock} on hand · {money(row.stockValue)} · {row.suggestion ?? "Quiet stock"}
              </Text>
            </Card>
          ))}
        </>
      )}
    </Screen>
  );
}
