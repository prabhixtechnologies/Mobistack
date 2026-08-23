import { useEffect, useState } from "react";
import { Pressable, Text, TextInput, View } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { selectedWorkspaceId } from "../lib/api";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { hasPermission } from "../lib/plan";
import { useTheme } from "../lib/theme";

interface Member {
  membershipId: string;
  fullName: string;
  email: string;
  role: string;
  status: string;
}

export default function MembersScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const workspaceId = selectedWorkspaceId(user);
  const canWrite = hasPermission(user, "USER_WRITE");
  const canInvite = hasPermission(user, "USER_INVITE") || canWrite;
  const [rows, setRows] = useState<Member[]>([]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("STAFF");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function load() {
    if (!workspaceId) {
      return;
    }
    const page = await api<{ content: Member[] }>(`/api/v1/workspaces/${workspaceId}/members?size=50`);
    setRows(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, [workspaceId]);

  return (
    <Screen title="Members" copy="Approve join requests and invite the next screen." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      {notice ? <Text style={{ color: colors.good, marginBottom: 10 }}>{notice}</Text> : null}
      {canInvite ? (
        <>
          <TextInput
            style={field(colors)}
            placeholder="Email to invite"
            placeholderTextColor={colors.faint}
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
          />
          <View style={{ flexDirection: "row", gap: 8, marginBottom: 12 }}>
            {["STAFF", "OWNER"].map((item) => (
              <Pressable
                key={item}
                onPress={() => setRole(item)}
                style={{
                  borderWidth: 1,
                  borderColor: role === item ? colors.ink : colors.line,
                  backgroundColor: role === item ? colors.ink : colors.card,
                  borderRadius: 999,
                  paddingHorizontal: 12,
                  paddingVertical: 6,
                }}
              >
                <Text style={{ color: role === item ? colors.bg : colors.ink, fontWeight: "700" }}>{item}</Text>
              </Pressable>
            ))}
          </View>
          <PrimaryButton
            label="Send invite"
            disabled={!email.trim()}
            onPress={() => {
              if (!workspaceId) {
                return;
              }
              void api(`/api/v1/workspaces/${workspaceId}/invitations`, {
                method: "POST",
                body: JSON.stringify({ email: email.trim(), role }),
              })
                .then(() => {
                  setEmail("");
                  setNotice("Invite sent.");
                })
                .catch((err: Error) => setError(err.message));
            }}
          />
        </>
      ) : null}
      {rows.map((row) => (
        <Card key={row.membershipId}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>{row.fullName}</Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>
            {row.email} · {row.role} · {row.status}
          </Text>
          {canWrite && row.status === "PENDING" ? (
            <View style={{ flexDirection: "row", gap: 12, marginTop: 10 }}>
              <Pressable
                onPress={() =>
                  void api(`/api/v1/workspaces/${workspaceId}/members/${row.membershipId}/approve`, {
                    method: "POST",
                    body: JSON.stringify({ role: "STAFF" }),
                  })
                    .then(load)
                    .catch((err: Error) => setError(err.message))
                }
              >
                <Text style={{ fontWeight: "700", color: colors.ink }}>Approve</Text>
              </Pressable>
              <Pressable
                onPress={() =>
                  void api(`/api/v1/workspaces/${workspaceId}/members/${row.membershipId}/reject`, { method: "POST" })
                    .then(load)
                    .catch((err: Error) => setError(err.message))
                }
              >
                <Text style={{ fontWeight: "700", color: colors.bad }}>Reject</Text>
              </Pressable>
            </View>
          ) : null}
        </Card>
      ))}
    </Screen>
  );
}

function field(colors: ReturnType<typeof useTheme>["colors"]) {
  return {
    backgroundColor: colors.card,
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    color: colors.ink,
  };
}
