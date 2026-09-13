import { useEffect, useState } from "react";
import { Linking, Text } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { IDENTITY_ISSUER } from "../lib/config";
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

  useEffect(() => {
    api<SessionCard[]>("/api/v1/auth/sessions")
      .then(setSessions)
      .catch(() => setSessions([]));
  }, []);

  return (
    <Screen title="Profile" copy="This account. Password changes happen on Prabhix Identity." back>
      <Card>
        <Text style={{ fontWeight: "700", color: colors.ink }}>{user?.fullName}</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>{user?.email}</Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>
          {user?.workspaceName ?? user?.shopName} · {user?.roles?.[0]}
        </Text>
        <Text style={{ color: colors.soft, marginTop: 4 }}>{user?.planName ?? "No plan"}</Text>
      </Card>
      <PrimaryButton
        label="Manage account on Identity"
        onPress={() => {
          void Linking.openURL(`${IDENTITY_ISSUER}/account`);
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
