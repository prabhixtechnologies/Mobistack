import { useEffect, useState } from "react";
import { Text } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { PaySheet } from "../components/PaySheet";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { confirmBillingOrder, needsRazorpay, rupees, type CheckoutOrder } from "../lib/pay";
import { money, useTheme } from "../lib/theme";

interface Plan {
  code: string;
  name: string;
  description?: string;
  amount: number;
  interval?: string;
  features?: string[];
}

interface Overview {
  plans: Plan[];
  subscription?: { planCode?: string; planName?: string; status?: string; periodEnd?: string; features?: string[] };
  screens?: { included: number; extra: number; seats: number; inUse: number; amount?: number; periodEnd?: string | null };
  recentPayments?: { id: string; planName?: string; amount: number; status?: string }[];
  paymentRequired?: boolean;
}

export default function BillingScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [pay, setPay] = useState<CheckoutOrder | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setOverview(await api<Overview>("/api/v1/billing"));
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function startPay(planCode: string) {
    setBusy(true);
    setError(null);
    try {
      const order = await api<CheckoutOrder>("/api/v1/billing/orders", {
        method: "POST",
        body: JSON.stringify({ planCode }),
      });
      if (needsRazorpay(order)) {
        setPay(order);
        return;
      }
      await confirmBillingOrder(order);
      setNotice("Plan is active on this shop.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start payment");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen title="Billing" copy="Activate the shop, add extra screens, and keep the subscription current." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      {notice ? <Text style={{ color: colors.good, marginBottom: 10 }}>{notice}</Text> : null}
      {overview?.subscription ? (
        <Card>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{overview.subscription.planName ?? "Current plan"}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {overview.subscription.status}
            {overview.subscription.periodEnd ? ` · until ${overview.subscription.periodEnd.slice(0, 10)}` : ""}
          </Text>
        </Card>
      ) : null}
      {overview?.screens ? (
        <Card>
          <Text style={{ fontWeight: "700", color: colors.ink }}>Screens</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {overview.screens.inUse} in use · {overview.screens.seats} seats · {overview.screens.extra} extra
          </Text>
          <PrimaryButton label="Add one extra screen" disabled={busy} onPress={() => void startPay("EXTRA_SCREEN")} />
        </Card>
      ) : null}
      {(overview?.plans ?? []).map((plan) => (
        <Card key={plan.code}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{plan.name}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>{plan.description}</Text>
          <Text style={{ color: colors.ink, marginTop: 8, fontWeight: "700" }}>
            {money(plan.amount)} / {(plan.interval ?? "MONTHLY").toLowerCase()}
          </Text>
          <PrimaryButton
            label={user?.planCode === plan.code ? "Current plan" : `Activate ${plan.name}`}
            disabled={busy || user?.planCode === plan.code}
            onPress={() => void startPay(plan.code)}
          />
        </Card>
      ))}
      {(overview?.recentPayments ?? []).map((payment) => (
        <Card key={payment.id}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{payment.planName ?? "Payment"}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {payment.status} · {money(payment.amount)}
          </Text>
        </Card>
      ))}
      {pay ? (
        <PaySheet
          order={pay}
          description="MobiStack shop plan"
          onCancel={() => setPay(null)}
          onPaid={(slip) => {
            void confirmBillingOrder(pay, slip)
              .then(() => {
                setPay(null);
                setNotice(`Paid ${rupees(pay.amount)}.`);
                return load();
              })
              .catch((err: Error) => {
                setPay(null);
                setError(err.message);
              });
          }}
        />
      ) : null}
    </Screen>
  );
}
