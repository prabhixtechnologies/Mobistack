/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_ORIGIN?: string;
  readonly VITE_PUBLIC_ORIGIN?: string;
  readonly VITE_RAZORPAY_KEY_ID?: string;
  readonly VITE_IDENTITY_ISSUER?: string;
  readonly VITE_ENVIRONMENT?: string;
  readonly VITE_APP_VERSION?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
