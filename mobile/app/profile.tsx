import { useEffect, useState } from "react";
import { Text, TextInput } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useTheme } from "../lib/theme";

interface SessionCard {
  id: string;
  deviceLabel?: string;
  current?: boolean;
  lastSeenAt?: string;
}

export default function ProfileScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const [sessions, setSessions] = useState<SessionCard[]>([]);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    api<SessionCard[]>("/api/v1/auth/sessions")
      .then(setSessions)
      .catch(() => setSessions([]));
  }, []);

  return (
    <Screen title="Profile" copy="This account. One live session at a time." back>
      <Card>
        <Text style={{ fontWeight: "700", color: colors.ink }}>{user?.fullName}</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>{user?.email}</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>
          {user?.workspaceName ?? user?.shopName} · {user?.roles?.[0]}
        </Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>{user?.planName ?? "No plan"}</Text>
      </Card>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      {notice ? <Text style={{ color: colors.good, marginBottom: 10 }}>{notice}</Text> : null}
      <TextInput
        style={field(colors)}
        placeholder="Current password"
        placeholderTextColor={colors.faint}
        secureTextEntry
        value={currentPassword}
        onChangeText={setCurrentPassword}
      />
      <TextInput
        style={field(colors)}
        placeholder="New password"
        placeholderTextColor={colors.faint}
        secureTextEntry
        value={newPassword}
        onChangeText={setNewPassword}
      />
      <PrimaryButton
        label="Change password"
        disabled={!currentPassword || newPassword.length < 8}
        onPress={() => {
          void api("/api/v1/auth/change-password", {
            method: "POST",
            body: JSON.stringify({ currentPassword, newPassword }),
          })
            .then(() => {
              setCurrentPassword("");
              setNewPassword("");
              setNotice("Password updated. Other screens were signed out.");
            })
            .catch((err: Error) => setError(err.message));
        }}
      />
      {sessions.map((session) => (
        <Card key={session.id}>
          <Text style={{ fontWeight: "700", color: colors.ink }}>
            {session.deviceLabel ?? "Device"} {session.current ? "(this phone)" : ""}
          </Text>
          <Text style={{ color: colors.soft, marginTop: 4 }}>{session.lastSeenAt ?? "Active"}</Text>
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
