import { api } from "./api";
import { BRAND } from "./brand";
import { checkoutContact, loadRazorpayCheckout, openRazorpayCheckout } from "./razorpay";

export interface CheckoutOrder {
  id: string;
  order_id?: string;
  orderId?: string;
  amount: number;
  currency: string;
  keyId?: string;
  priceCode: string;
  gateway: string;
}

export function razorpayOrderId(order: CheckoutOrder): string {
  return (order.order_id || order.orderId || "").trim();
}

export async function captureCheckoutOrder(
  order: CheckoutOrder,
  user?: { fullName?: string; email?: string; phone?: string },
  description?: string,
  publishableKey?: string,
): Promise<void> {
  const remoteOrderId = razorpayOrderId(order);
  if (order.gateway === "DEV") {
    await api(`/api/v1/mobistack/billing/orders/confirm?id=${order.id}`, { method: "POST" });
    return;
  }
  if (order.gateway === "PAID") {
    return;
  }
  if (!remoteOrderId.startsWith("order_")) {
    throw new Error("The server did not return a Razorpay order. Try again.");
  }
  const key = (order.keyId || publishableKey || "").trim();
  if (!key) {
    throw new Error("Razorpay key is missing. Set VITE_RAZORPAY_KEY_ID or configure the server.");
  }
  await loadRazorpayCheckout();
  const testMode = key.startsWith("rzp_test_");
  await new Promise<void>((resolve, reject) => {
    let settled = false;
    const checkout = openRazorpayCheckout({
      key,
      amount: order.amount,
      currency: order.currency,
      name: BRAND.product,
      description: description || "MobiStack payment",
      order_id: remoteOrderId,
      remember_customer: false,
      retry: { enabled: true, max_count: 3 },
      prefill: {
        name: user?.fullName,
        email: user?.email,
        contact: checkoutContact(user?.phone),
        method: testMode ? "card" : undefined,
      },
      method: testMode
        ? { card: true, netbanking: true, wallet: true, upi: false, emi: false, paylater: false }
        : undefined,
      theme: { color: "#0e7490" },
      handler: (response) => {
        void api("/api/v1/mobistack/billing/verify", {
          method: "POST",
          body: JSON.stringify({
            razorpay_order_id: response.razorpay_order_id,
            razorpay_payment_id: response.razorpay_payment_id,
            razorpay_signature: response.razorpay_signature,
          }),
        })
          .then(() => {
            settled = true;
            resolve();
          })
          .catch((err: Error) => {
            settled = true;
            reject(err);
          });
      },
      modal: {
        ondismiss: () => {
          if (!settled) {
            reject(new Error("Payment cancelled."));
          }
        },
      },
    });
    checkout.on("payment.failed", (response) => {
      settled = true;
      reject(new Error(response.error?.description || "Payment failed."));
    });
    checkout.open();
  });
}

export async function collectJoinPayment(
  order: CheckoutOrder,
  joinCode: string,
  user?: { fullName?: string; email?: string; phone?: string },
  shopName?: string,
  publishableKey?: string,
): Promise<{ razorpay_order_id?: string; razorpay_payment_id?: string; razorpay_signature?: string; orderId?: string }> {
  if (order.gateway === "PAID") {
    return {};
  }
  if (order.gateway === "DEV") {
    return { orderId: order.id };
  }
  const remoteOrderId = razorpayOrderId(order);
  if (!remoteOrderId.startsWith("order_")) {
    throw new Error("The server did not return a Razorpay order. Try again.");
  }
  const key = (order.keyId || publishableKey || "").trim();
  if (!key) {
    throw new Error("Razorpay key is missing. Set VITE_RAZORPAY_KEY_ID or configure the server.");
  }
  await loadRazorpayCheckout();
  const testMode = key.startsWith("rzp_test_");
  return new Promise((resolve, reject) => {
    let settled = false;
    const checkout = openRazorpayCheckout({
      key,
      amount: order.amount,
      currency: order.currency,
      name: BRAND.product,
      description: shopName ? `Join ${shopName}` : `Join ${joinCode}`,
      order_id: remoteOrderId,
      remember_customer: false,
      retry: { enabled: true, max_count: 3 },
      prefill: {
        name: user?.fullName,
        email: user?.email,
        contact: checkoutContact(user?.phone),
        method: testMode ? "card" : undefined,
      },
      method: testMode
        ? { card: true, netbanking: true, wallet: true, upi: false, emi: false, paylater: false }
        : undefined,
      theme: { color: "#0e7490" },
      handler: (response) => {
        settled = true;
        resolve({
          razorpay_order_id: response.razorpay_order_id,
          razorpay_payment_id: response.razorpay_payment_id,
          razorpay_signature: response.razorpay_signature,
        });
      },
      modal: {
        ondismiss: () => {
          if (!settled) {
            reject(new Error("Payment cancelled."));
          }
        },
      },
    });
    checkout.on("payment.failed", (response) => {
      settled = true;
      reject(new Error(response.error?.description || "Payment failed."));
    });
    checkout.open();
  });
}
