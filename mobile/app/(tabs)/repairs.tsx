import { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { api } from "../../lib/api";
import { cachedRepairs, type CachedRepair } from "../../lib/offline";
import { enqueue } from "../../lib/outbox";
import { money, useTheme } from "../../lib/theme";

export default function RepairsScreen() {
  const { colors } = useTheme();
  const [jobs, setJobs] = useState<CachedRepair[]>([]);
  const [problem, setProblem] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const styles = makeStyles(colors);

  async function load() {
    try {
      const page = await api<{ content: CachedRepair[] }>("/api/v1/repairs?size=30");
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
    const payload = { problem, laborCharge: 500, idempotencyKey };
    try {
      await api("/api/v1/repairs", { method: "POST", body: JSON.stringify(payload) });
    } catch {
      await enqueue({ type: "REPAIR", idempotencyKey, repair: payload });
      setJobs((current) => [
        { id: idempotencyKey, jobNumber: "OFFLINE", status: "QUEUED", problem, total: 500, outstanding: 500 },
        ...current,
      ]);
    }
    setProblem("");
    await load();
  }

  async function advance(job: CachedRepair) {
    if (job.id.length < 20) {
      return;
    }
    const next = job.status === "RECEIVED" ? "IN_REPAIR" : job.status === "IN_REPAIR" ? "READY" : "DELIVERED";
    await api(`/api/v1/repairs/${job.id}`, { method: "PUT", body: JSON.stringify({ status: next }) });
    await load();
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={styles.page}>
      <Text style={styles.title}>Repairs</Text>
      <Text style={styles.copy}>Open a job at the counter. Parts come off the same stock ledger as sales.</Text>
      {offline ? <Text style={styles.banner}>Jobs stay on this phone until sync.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput
        style={styles.search}
        placeholder="What is wrong with the phone?"
        placeholderTextColor={colors.faint}
        value={problem}
        onChangeText={setProblem}
      />
      <Pressable style={styles.btn} onPress={() => void create()} disabled={!problem.trim()}>
        <Text style={styles.btnText}>Open job</Text>
      </Pressable>
      {jobs.map((job) => (
        <Pressable key={job.id} style={styles.card} onPress={() => void advance(job)}>
          <Text style={styles.name}>{job.jobNumber}</Text>
          <Text style={styles.sub}>{job.problem}</Text>
          <Text style={styles.sub}>
            {job.status} · {money(job.total)}
          </Text>
        </Pressable>
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
