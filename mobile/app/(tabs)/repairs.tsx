import { useCallback, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedRepairs, type CachedRepair } from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
import { useAction } from "../../lib/useAction";
import { useScreenData } from "../../lib/useScreenData";
import { Empty, Failed, Loading, OfflineNotice, Problem } from "../../components/ListState";
import { Screen } from "../../components/Screen";
import { money, useTheme } from "../../lib/theme";

interface Job extends CachedRepair {
  customerName?: string;
  imei?: string;
  paid?: number;
}

const NEXT: Record<string, string> = {
  RECEIVED: "DIAGNOSING",
  DIAGNOSING: "IN_REPAIR",
  IN_REPAIR: "READY",
  READY: "DELIVERED",
};

/**
 * A job created while offline has no server id yet, so it cannot be paid for or
 * have parts fitted until it syncs. The queued status is what marks it, rather
 * than guessing from the shape of the id.
 */
const QUEUED = "QUEUED";

export default function RepairsScreen() {
  const { colors } = useTheme();
  const styles = makeStyles(colors);
  const [problem, setProblem] = useState("");
  const [customer, setCustomer] = useState("");
  const [imei, setImei] = useState("");
  const [labor, setLabor] = useState("500");
  const [queued, setQueued] = useState<Job[]>([]);
  const [partJob, setPartJob] = useState<string | null>(null);
  const [partQuery, setPartQuery] = useState("");

  const load = useCallback(async () => {
    const page = await api<{ content: Job[] }>("/api/v1/repairs?size=30");
    return page.content;
  }, []);
  const jobs = useScreenData<Job[]>("repairs", load, { fallback: cachedRepairs });

  const create = useAction(
    async () => {
      const idempotencyKey = `${Date.now()}`;
      const charge = Number(labor) || 0;
      const payload = {
        problem,
        customerNotes: customer || undefined,
        imei: imei || undefined,
        laborCharge: charge,
        idempotencyKey,
      };
      try {
        await api("/api/v1/repairs", { method: "POST", body: JSON.stringify(payload) });
      } catch {
        await enqueue({ type: "REPAIR", idempotencyKey, repair: payload });
        setQueued((current) => [
          {
            id: idempotencyKey,
            jobNumber: "Waiting to sync",
            status: QUEUED,
            problem,
            total: charge,
            outstanding: charge,
          },
          ...current,
        ]);
      }
      setProblem("");
      setCustomer("");
      setImei("");
      jobs.refresh();
    },
    { fallbackError: "Could not open that job." },
  );

  const advance = useAction(
    async (job: Job) => {
      const next = NEXT[job.status] ?? "DELIVERED";
      try {
        await api(`/api/v1/repairs/${job.id}`, { method: "PUT", body: JSON.stringify({ status: next }) });
      } catch {
        await enqueue({
          type: "REPAIR_STATUS",
          idempotencyKey: `${job.id}-${next}`,
          repairStatus: { repairId: job.id, status: next },
        });
      }
      jobs.refresh();
    },
    { fallbackError: "Could not move that job on." },
  );

  const collect = useAction(
    async (job: Job) => {
      await api(`/api/v1/repairs/${job.id}/payments`, {
        method: "POST",
        headers: { "Idempotency-Key": `${job.id}-pay-${job.outstanding}` },
        body: JSON.stringify({ payments: [{ method: "CASH", amount: job.outstanding }] }),
      });
      jobs.refresh();
    },
    { fallbackError: "Could not take that payment. It has not been recorded." },
  );

  const fitPart = useAction(
    async (jobId: string, sku: string) => {
      const result = await api<{ parts: { variantId: string; price: number }[] }>(
        `/api/v1/search?q=${encodeURIComponent(sku)}`,
      );
      const part = result.parts?.[0];
      if (!part) {
        throw new Error(`No part matches "${sku}".`);
      }
      await api(`/api/v1/repairs/${jobId}/parts`, {
        method: "POST",
        body: JSON.stringify({ variantId: part.variantId, quantity: 1, unitPrice: part.price }),
      });
      setPartQuery("");
      setPartJob(null);
      jobs.refresh();
    },
    { fallbackError: "Could not fit that part." },
  );

  const problems = [create.error, advance.error, collect.error, fitPart.error].filter(Boolean) as string[];
  const rows = [...queued, ...(jobs.data ?? [])];

  return (
    <Screen
      title="Repairs"
      copy="Open a job, add parts from stock, collect cash or UPI, then mark it delivered."
      onRefresh={jobs.refresh}
      refreshing={jobs.refreshing}
    >
      {jobs.offline ? <OfflineNotice what="jobs" /> : null}
      {problems.map((message) => (
        <Problem message={message} key={message} />
      ))}

      <TextInput
        style={styles.search}
        placeholder="What is wrong with the phone?"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Problem description"
        value={problem}
        onChangeText={setProblem}
      />
      <TextInput
        style={styles.search}
        placeholder="Customer name (optional)"
        placeholderTextColor={colors.faint}
        accessibilityLabel="Customer name"
        value={customer}
        onChangeText={setCustomer}
      />
      <TextInput
        style={styles.search}
        placeholder="IMEI (optional)"
        placeholderTextColor={colors.faint}
        accessibilityLabel="IMEI"
        value={imei}
        onChangeText={setImei}
      />
      <TextInput
        style={styles.search}
        placeholder="Labor"
        placeholderTextColor={colors.faint}
        keyboardType="numeric"
        accessibilityLabel="Labor charge"
        value={labor}
        onChangeText={setLabor}
      />
      <Pressable
        style={[styles.btn, (create.busy || !problem.trim()) && { opacity: 0.5 }]}
        onPress={() => void create.run()}
        disabled={create.busy || !problem.trim()}
      >
        <Text style={styles.btnText}>{create.busy ? "Opening…" : "Open job"}</Text>
      </Pressable>

      {jobs.loading && rows.length === 0 ? (
        <Loading label="Loading the bench…" />
      ) : jobs.error && rows.length === 0 ? (
        <Failed message={jobs.error} onRetry={jobs.refresh} />
      ) : rows.length === 0 ? (
        <Empty title="Nothing on the bench" hint="Open a job above and it stays here until it is delivered." />
      ) : (
        rows.map((job) => {
          const synced = job.status !== QUEUED;
          return (
            <View key={job.id} style={styles.card}>
              <Text style={styles.name}>{job.jobNumber}</Text>
              <Text style={styles.sub}>{job.problem}</Text>
              <Text style={styles.sub}>
                {job.status} · {money(job.total)} · due {money(job.outstanding)}
              </Text>
              {!synced ? (
                <Text style={[styles.sub, { color: colors.warn }]}>
                  Parts and payment open once this job reaches the server.
                </Text>
              ) : null}
              <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 16, marginTop: 10 }}>
                {job.status !== "DELIVERED" && synced ? (
                  <Pressable
                    hitSlop={12}
                    style={{ minHeight: 44, justifyContent: "center" }}
                    disabled={advance.busy}
                    onPress={() => void advance.run(job)}
                  >
                    <Text style={[styles.link, advance.busy && { opacity: 0.5 }]}>
                      {advance.busy ? "Moving…" : `Move to ${(NEXT[job.status] ?? "DELIVERED").replace("_", " ")}`}
                    </Text>
                  </Pressable>
                ) : null}
                {job.outstanding > 0 && synced ? (
                  <Pressable
                    hitSlop={12}
                    style={{ minHeight: 44, justifyContent: "center" }}
                    disabled={collect.busy}
                    onPress={() => void collect.run(job)}
                  >
                    <Text style={[styles.link, collect.busy && { opacity: 0.5 }]}>
                      {collect.busy ? "Taking…" : `Collect ${money(job.outstanding)}`}
                    </Text>
                  </Pressable>
                ) : null}
                {synced ? (
                  <Pressable
                    hitSlop={12}
                    style={{ minHeight: 44, justifyContent: "center" }}
                    onPress={() => setPartJob(partJob === job.id ? null : job.id)}
                  >
                    <Text style={styles.link}>Add part</Text>
                  </Pressable>
                ) : null}
              </View>
              {partJob === job.id ? (
                <TextInput
                  style={[styles.search, { marginTop: 10, marginBottom: 0 }]}
                  placeholder="SKU, then enter"
                  placeholderTextColor={colors.faint}
                  accessibilityLabel="Part SKU"
                  value={partQuery}
                  onChangeText={setPartQuery}
                  editable={!fitPart.busy}
                  autoCorrect={false}
                  onSubmitEditing={() => {
                    if (partQuery.trim()) {
                      void fitPart.run(job.id, partQuery.trim());
                    }
                  }}
                />
              ) : null}
            </View>
          );
        })
      )}
    </Screen>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    search: {
      backgroundColor: colors.card,
      borderColor: colors.line,
      borderWidth: 1,
      borderRadius: 16,
      padding: 14,
      marginBottom: 12,
      color: colors.ink,
    },
    card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginBottom: 10 },
    name: { fontWeight: "700", color: colors.ink },
    sub: { color: colors.soft, marginTop: 4 },
    link: { fontWeight: "700", color: colors.ink },
    btn: { backgroundColor: colors.accent, borderRadius: 14, padding: 14, alignItems: "center", marginBottom: 16, minHeight: 44, justifyContent: "center" },
    btnText: { color: colors.accentInk, fontWeight: "700" },
  });
}
