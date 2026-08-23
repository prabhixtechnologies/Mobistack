import { api } from "./api";

export interface CheckoutOrder {
  id: string;
  order_id?: string | null;
  amount: number;
  currency: string;
  keyId?: string | null;
  priceCode?: string;
  gateway: string;
  shopName?: string;
  alreadyPaid?: boolean;
}

export interface RazorpaySlip {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

export function needsRazorpay(order: CheckoutOrder): boolean {
  return Boolean(order.keyId && order.order_id && order.gateway !== "DEV" && order.gateway !== "PAID" && !order.alreadyPaid);
}

export async function confirmBillingOrder(order: CheckoutOrder, slip?: RazorpaySlip): Promise<void> {
  if (slip) {
    await api("/api/v1/billing/verify", { method: "POST", body: JSON.stringify(slip) });
    return;
  }
  await api(`/api/v1/billing/orders/${order.id}/confirm`, { method: "POST" });
}

export function rupees(amountPaise: number): string {
  return `₹${(amountPaise / 100).toFixed(0)}`;
}
