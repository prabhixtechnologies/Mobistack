import { useEffect, useState } from "react";
import { Text, TextInput } from "react-native";
import { Card, PrimaryButton, Screen } from "../components/Screen";
import { api } from "../lib/api";
import { hasPermission } from "../lib/plan";
import { useAuth } from "../lib/auth";
import { useTheme } from "../lib/theme";

interface Shop {
  name: string;
  phone?: string;
  email?: string;
  city?: string;
  gstNumber?: string;
  joinCode?: string;
  extraScreens?: number;
  screenSeats?: number;
  screensInUse?: number;
}

export default function SettingsScreen() {
  const { user } = useAuth();
  const { colors } = useTheme();
  const canWrite = hasPermission(user, "SETTINGS_WRITE");
  const [shop, setShop] = useState<Shop | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    api<Shop>("/api/v1/shop")
      .then(setShop)
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <Screen title="Settings" copy="Shop profile and join code for the next staff phone." back>
      {error ? <Text style={{ color: colors.bad, marginBottom: 10 }}>{error}</Text> : null}
      {notice ? <Text style={{ color: colors.good, marginBottom: 10 }}>{notice}</Text> : null}
      {shop ? (
        <>
          <Card>
            <Text style={{ fontWeight: "700", color: colors.ink }}>Join code</Text>
            <Text style={{ color: colors.ink, marginTop: 6, fontSize: 22 }}>{shop.joinCode ?? "—"}</Text>
            <Text style={{ color: colors.soft, marginTop: 4 }}>
              {shop.screensInUse ?? 0} screens in use · {shop.screenSeats ?? 1} seats
            </Text>
          </Card>
          <TextInput
            style={field(colors)}
            value={shop.name}
            editable={canWrite}
            accessibilityLabel="Shop name"
            onChangeText={(name) => setShop({ ...shop, name })}
          />
          <TextInput
            style={field(colors)}
            value={shop.phone ?? ""}
            editable={canWrite}
            placeholder="Phone"
            placeholderTextColor={colors.faint}
            accessibilityLabel="Shop phone"
            onChangeText={(phone) => setShop({ ...shop, phone })}
          />
          <TextInput
            style={field(colors)}
            value={shop.city ?? ""}
            editable={canWrite}
            placeholder="City"
            placeholderTextColor={colors.faint}
            accessibilityLabel="Shop city"
            onChangeText={(city) => setShop({ ...shop, city })}
          />
          <TextInput
            style={field(colors)}
            value={shop.gstNumber ?? ""}
            editable={canWrite}
            placeholder="GST"
            placeholderTextColor={colors.faint}
            accessibilityLabel="GST number"
            onChangeText={(gstNumber) => setShop({ ...shop, gstNumber })}
          />
          {canWrite ? (
            <PrimaryButton
              label="Save shop"
              onPress={() => {
                void api("/api/v1/shop", {
                  method: "PUT",
                  body: JSON.stringify({
                    name: shop.name,
                    phone: shop.phone,
                    city: shop.city,
                    gstNumber: shop.gstNumber,
                  }),
                })
                  .then((updated) => {
                    setShop(updated as Shop);
                    setNotice("Shop saved.");
                  })
                  .catch((err: Error) => setError(err.message));
              }}
            />
          ) : null}
        </>
      ) : null}
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
