import { useState } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import { useTheme } from "../lib/theme";

export function BarcodeScanButton({ onScan }: { onScan: (value: string) => void }) {
  const { colors } = useTheme();
  const [open, setOpen] = useState(false);
  const [permission, requestPermission] = useCameraPermissions();

  return (
    <>
      <Pressable
        style={{
          borderColor: colors.line,
          borderWidth: 1,
          borderRadius: 14,
          padding: 14,
          alignItems: "center",
          marginBottom: 12,
        }}
        onPress={async () => {
          if (!permission?.granted) {
            const next = await requestPermission();
            if (!next.granted) {
              return;
            }
          }
          setOpen(true);
        }}
      >
        <Text style={{ fontWeight: "700", color: colors.ink }}>Scan barcode</Text>
      </Pressable>
      <Modal visible={open} animationType="slide" onRequestClose={() => setOpen(false)}>
        <View style={{ flex: 1, backgroundColor: colors.ink }}>
          <CameraView
            style={StyleSheet.absoluteFill}
            barcodeScannerSettings={{ barcodeTypes: ["ean13", "ean8", "code128", "code39", "qr", "upc_a", "upc_e"] }}
            onBarcodeScanned={({ data }) => {
              setOpen(false);
              onScan(data);
            }}
          />
          <Pressable
            style={{ position: "absolute", top: 56, right: 20, backgroundColor: colors.card, padding: 12, borderRadius: 12 }}
            onPress={() => setOpen(false)}
          >
            <Text style={{ fontWeight: "700", color: colors.ink }}>Close</Text>
          </Pressable>
        </View>
      </Modal>
    </>
  );
}
