/** Human labels for codes the API still speaks in SCREAMING_SNAKE. */

const WORDS: Record<string, string> = {
  RECEIVED: "Received",
  DIAGNOSING: "Diagnosing",
  WAITING_FOR_PART: "Waiting for a part",
  IN_REPAIR: "In repair",
  READY: "Ready for pickup",
  DELIVERED: "Collected",
  CANCELLED: "Cancelled",
  COMPLETED: "Completed",
  VOIDED: "Voided",
  PENDING: "Pending",
  RETAIL: "Retail",
  WHOLESALE: "Wholesale",
  VIP: "VIP",
  CASH: "Cash",
  UPI: "UPI",
  CARD: "Card",
  CREDIT: "Credit",
  GREEN: "In stock",
  ORANGE: "Low stock",
  RED: "Out of stock",
  LOW_STOCK: "Low stock",
  OUT_OF_STOCK: "Out of stock",
  IN: "Stock received",
  OUT: "Stock out",
  RETURN: "Return",
  DAMAGE: "Write-off",
  ADJUSTMENT: "Adjustment",
  OPENING: "Opening stock",
  TRANSFER: "Transfer",
  RESERVATION: "Reserved",
  RELEASE: "Released",
};

export function humanLabel(code?: string | null): string {
  if (!code) {
    return "—";
  }
  return WORDS[code] ?? code.replaceAll("_", " ").toLowerCase().replace(/^\w/, (ch) => ch.toUpperCase());
}
