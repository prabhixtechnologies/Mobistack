export interface RazorpaySuccessResponse {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

export interface RazorpayFailureResponse {
  error?: { description?: string; reason?: string; code?: string };
}

export interface RazorpayCheckoutOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description?: string;
  order_id: string;
  prefill?: {
    name?: string;
    email?: string;
    contact?: string;
    method?: "card" | "upi" | "netbanking" | "wallet";
  };
  theme?: { color?: string };
  modal?: {
    ondismiss?: () => void;
    confirm_close?: boolean;
    escape?: boolean;
    backdropclose?: boolean;
  };
  remember_customer?: boolean;
  retry?: { enabled?: boolean; max_count?: number };
  method?: {
    card?: boolean;
    upi?: boolean;
    netbanking?: boolean;
    wallet?: boolean;
    emi?: boolean;
    paylater?: boolean;
  };
  config?: {
    display?: {
      hide?: { method: string }[];
      preferences?: { show_default_blocks?: boolean };
    };
  };
  handler: (response: RazorpaySuccessResponse) => void;
}

/** Razorpay wants E.164, for example +91XXXXXXXXXX. */
export function checkoutContact(phone?: string | null): string | undefined {
  if (!phone) {
    return undefined;
  }
  const digits = phone.replace(/\D/g, "");
  if (digits.length === 10) {
    return `+91${digits}`;
  }
  if (digits.length === 12 && digits.startsWith("91")) {
    return `+${digits}`;
  }
  if (phone.trim().startsWith("+") && digits.length >= 10) {
    return `+${digits}`;
  }
  return phone.trim() || undefined;
}

interface RazorpayInstance {
  open: () => void;
  on: (event: "payment.failed", handler: (response: RazorpayFailureResponse) => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayInstance;
  }
}

export function loadRazorpayCheckout(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>("script[data-razorpay-checkout]");
    const script = existing ?? document.createElement("script");
    if (window.Razorpay) {
      resolve();
      return;
    }
    const timer = window.setTimeout(() => {
      reject(new Error("Razorpay Checkout timed out. Disable the ad blocker and try again."));
    }, 15000);
    const succeed = () => {
      window.clearTimeout(timer);
      if (window.Razorpay) {
        resolve();
        return;
      }
      reject(new Error("Could not load Razorpay Checkout"));
    };
    script.addEventListener("load", succeed, { once: true });
    script.addEventListener("error", () => {
      window.clearTimeout(timer);
      reject(new Error("Could not load Razorpay Checkout"));
    }, { once: true });
    if (!existing) {
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.async = true;
      script.dataset.razorpayCheckout = "true";
      document.body.appendChild(script);
    }
  });
}

export function openRazorpayCheckout(options: RazorpayCheckoutOptions): RazorpayInstance {
  if (!window.Razorpay) {
    throw new Error("Razorpay Checkout is not loaded.");
  }
  document.body.classList.add("rzp-checkout-open");
  const clear = () => document.body.classList.remove("rzp-checkout-open");
  return new window.Razorpay({
    ...options,
    handler: (response) => {
      clear();
      options.handler(response);
    },
    modal: {
      ...options.modal,
      ondismiss: () => {
        clear();
        options.modal?.ondismiss?.();
      },
    },
  });
}
