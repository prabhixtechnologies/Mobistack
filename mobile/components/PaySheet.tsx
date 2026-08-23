import { useMemo } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { WebView } from "react-native-webview";
import type { CheckoutOrder, RazorpaySlip } from "../lib/pay";
import { rupees } from "../lib/pay";
import { useTheme } from "../lib/theme";

export function PaySheet({
  order,
  description,
  onPaid,
  onCancel,
}: {
  order: CheckoutOrder;
  description: string;
  onPaid: (slip: RazorpaySlip) => void;
  onCancel: () => void;
}) {
  const { colors } = useTheme();
  const html = useMemo(() => checkoutHtml(order, description), [order, description]);

  return (
    <Modal visible animationType="slide" onRequestClose={onCancel}>
      <View style={{ flex: 1, backgroundColor: colors.bg, paddingTop: 48 }}>
        <View style={styles.bar}>
          <Text style={[styles.title, { color: colors.ink }]}>Pay {rupees(order.amount)}</Text>
          <Pressable onPress={onCancel}>
            <Text style={{ color: colors.soft, fontWeight: "700" }}>Cancel</Text>
          </Pressable>
        </View>
        <WebView
          originWhitelist={["*"]}
          source={{ html, baseUrl: "https://mobistack.prabhixtechnologies.com" }}
          onMessage={(event) => {
            try {
              const payload = JSON.parse(event.nativeEvent.data) as {
                ok?: boolean;
                cancelled?: boolean;
                error?: string;
                razorpay_order_id?: string;
                razorpay_payment_id?: string;
                razorpay_signature?: string;
              };
              if (payload.cancelled || !payload.ok) {
                onCancel();
                return;
              }
              if (payload.razorpay_order_id && payload.razorpay_payment_id && payload.razorpay_signature) {
                onPaid({
                  razorpay_order_id: payload.razorpay_order_id,
                  razorpay_payment_id: payload.razorpay_payment_id,
                  razorpay_signature: payload.razorpay_signature,
                });
              }
            } catch {
              onCancel();
            }
          }}
        />
      </View>
    </Modal>
  );
}

function checkoutHtml(order: CheckoutOrder, description: string): string {
  const safe = (value: string) => value.replace(/[<>\\'"]/g, "");
  return `<!DOCTYPE html>
<html>
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
  </head>
  <body style="background:#14130F;color:#fff;font-family:sans-serif;padding:24px">
    <p>Opening Razorpay…</p>
    <script>
      var options = {
        key: "${safe(order.keyId ?? "")}",
        amount: ${Number(order.amount) || 0},
        currency: "${safe(order.currency || "INR")}",
        name: "MobiStack",
        description: "${safe(description)}",
        order_id: "${safe(order.order_id ?? "")}",
        theme: { color: "#14130F" },
        handler: function (response) {
          window.ReactNativeWebView.postMessage(JSON.stringify({
            ok: true,
            razorpay_order_id: response.razorpay_order_id,
            razorpay_payment_id: response.razorpay_payment_id,
            razorpay_signature: response.razorpay_signature
          }));
        },
        modal: {
          ondismiss: function () {
            window.ReactNativeWebView.postMessage(JSON.stringify({ ok: false, cancelled: true }));
          }
        }
      };
      var rzp = new Razorpay(options);
      rzp.on("payment.failed", function (resp) {
        window.ReactNativeWebView.postMessage(JSON.stringify({
          ok: false,
          error: (resp && resp.error && resp.error.description) || "Payment failed"
        }));
      });
      rzp.open();
    </script>
  </body>
</html>`;
}

const styles = StyleSheet.create({
  bar: {
    paddingHorizontal: 20,
    paddingBottom: 12,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  title: { fontSize: 20, fontWeight: "700" },
});
