import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedRepairs, type CachedRepair } from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
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

export default function RepairsScreen() {
  const { colors } = useTheme();
  const [jobs, setJobs] = useState<Job[]>([]);
  const [problem, setProblem] = useState("");
  const [customer, setCustomer] = useState("");
  const [imei, setImei] = useState("");
  const [labor, setLabor] = useState("500");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [partJob, setPartJob] = useState<string | null>(null);
  const [partQuery, setPartQuery] = useState("");
  const styles = makeStyles(colors);

  async function load() {
    try {
      const page = await api<{ content: Job[] }>("/api/v1/repairs?size=30");
      setJobs(page.content);
      setOffline(false);
    } catch {
      setJobs(await cachedRepairs());
      setOffline(true);
    }
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function create() {
    const idempotencyKey = `${Date.now()}`;
    const payload = {
      problem,
      customerNotes: customer || undefined,
      imei: imei || undefined,
      laborCharge: Number(labor) || 0,
      idempotencyKey,
    };
    try {
      await api("/api/v1/repairs", { method: "POST", body: JSON.stringify(payload) });
    } catch {
      await enqueue({ type: "REPAIR", idempotencyKey, repair: payload });
      setJobs((current) => [
        { id: idempotencyKey, jobNumber: "OFFLINE", status: "QUEUED", problem, total: Number(labor) || 0, outstanding: Number(labor) || 0 },
        ...current,
      ]);
    }
    setProblem("");
    setCustomer("");
    setImei("");
    await load();
  }

  async function advance(job: Job) {
    if (job.id.length < 20) {
      return;
    }
    const next = NEXT[job.status] ?? "DELIVERED";
    try {
      await api(`/api/v1/repairs/${job.id}`, { method: "PUT", body: JSON.stringify({ status: next }) });
    } catch {
      await enqueue({
        type: "REPAIR_STATUS",
        idempotencyKey: `${job.id}-${next}`,
        repairStatus: { repairId: job.id, status: next },
      });
      setJobs((current) => current.map((row) => (row.id === job.id ? { ...row, status: next } : row)));
      return;
    }
    await load();
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>Repairs</Text>
      <Text style={styles.copy}>Open a job, add parts from stock, collect cash or UPI, then mark it delivered.</Text>
      {offline ? <Text style={styles.banner}>Jobs stay on this phone until sync.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput style={styles.search} placeholder="What is wrong with the phone?" placeholderTextColor={colors.faint} value={problem} onChangeText={setProblem} />
      <TextInput style={styles.search} placeholder="Customer name (optional)" placeholderTextColor={colors.faint} value={customer} onChangeText={setCustomer} />
      <TextInput style={styles.search} placeholder="IMEI (optional)" placeholderTextColor={colors.faint} value={imei} onChangeText={setImei} />
      <TextInput style={styles.search} placeholder="Labor" placeholderTextColor={colors.faint} keyboardType="numeric" value={labor} onChangeText={setLabor} />
      <Pressable style={styles.btn} onPress={() => void create()} disabled={!problem.trim()}>
        <Text style={styles.btnText}>Open job</Text>
      </Pressable>
      {jobs.map((job) => (
        <View key={job.id} style={styles.card}>
          <Text style={styles.name}>{job.jobNumber}</Text>
          <Text style={styles.sub}>{job.problem}</Text>
          <Text style={styles.sub}>
            {job.status} · {money(job.total)} · due {money(job.outstanding)}
          </Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 12, marginTop: 10 }}>
            {job.status !== "DELIVERED" && job.status !== "QUEUED" ? (
              <Pressable onPress={() => void advance(job)}>
                <Text style={{ fontWeight: "700", color: colors.ink }}>Advance</Text>
              </Pressable>
            ) : null}
            {job.outstanding > 0 && job.id.length > 20 ? (
              <Pressable
                onPress={() =>
                  void api(`/api/v1/repairs/${job.id}/payments`, {
                    method: "POST",
                    body: JSON.stringify({ payments: [{ method: "CASH", amount: job.outstanding }] }),
                  })
                    .then(load)
                    .catch((err: Error) => setError(err.message))
                }
              >
                <Text style={{ fontWeight: "700", color: colors.ink }}>Collect {money(job.outstanding)}</Text>
              </Pressable>
            ) : null}
            {job.id.length > 20 ? (
              <Pressable onPress={() => setPartJob(partJob === job.id ? null : job.id)}>
                <Text style={{ fontWeight: "700", color: colors.ink }}>Add part</Text>
              </Pressable>
            ) : null}
          </View>
          {partJob === job.id ? (
            <TextInput
              style={[styles.search, { marginTop: 10, marginBottom: 0 }]}
              placeholder="SKU then enter"
              placeholderTextColor={colors.faint}
              value={partQuery}
              onChangeText={setPartQuery}
              onSubmitEditing={() => {
                void api<{ parts: { variantId: string; price: number }[] }>(`/api/v1/search?q=${encodeURIComponent(partQuery)}`)
                  .then((result) => {
                    const part = result.parts?.[0];
                    if (!part) {
                      throw new Error("No matching part");
                    }
                    return api(`/api/v1/repairs/${job.id}/parts`, {
                      method: "POST",
                      body: JSON.stringify({ variantId: part.variantId, quantity: 1, unitPrice: part.price }),
                    });
                  })
                  .then(() => {
                    setPartQuery("");
                    setPartJob(null);
                    return load();
                  })
                  .catch((err: Error) => setError(err.message));
              }}
            />
          ) : null}
        </View>
      ))}
    </ScrollView>
  );
}

function makeStyles(colors: ReturnType<typeof useTheme>["colors"]) {
  return StyleSheet.create({
    page: { padding: 22, paddingTop: 62, paddingBottom: 40 },
    title: { fontSize: 32, fontWeight: "500", color: colors.ink },
    copy: { color: colors.soft, marginTop: 8, marginBottom: 16, lineHeight: 22 },
    banner: { color: colors.warn, marginBottom: 12, fontWeight: "600" },
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
    btn: { backgroundColor: colors.ink, borderRadius: 14, padding: 14, alignItems: "center", marginBottom: 16 },
    btnText: { color: colors.bg, fontWeight: "700" },
    error: { color: colors.bad, marginBottom: 8 },
  });
}
