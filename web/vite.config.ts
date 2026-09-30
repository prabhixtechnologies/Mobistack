import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
    dedupe: ["react", "react-dom"],
  },
  // `optimizeDeps.include` for @prabhixtechnologies/oidc-client used to sit here, and so did a
  // `server.fs.allow` entry pointing at the real path of a sibling web-kit checkout. Both existed
  // because brand and oidc-client were `file:` links leaving this repository: Vite excludes linked
  // packages from pre-bundling, and it resolves a link to its real path before checking fs.allow,
  // so brand's `new URL("../marks/…", import.meta.url)` resolved outside the allowed roots and
  // failed as "Denied ID". They install from GitHub Packages now, so there is no link to allow
  // and nothing to opt back into pre-bundling.
  server: {
    // Local Identity redirect is :5176; compose publishes API on :8082.
    port: 5176,
    fs: { allow: [path.resolve(import.meta.dirname, "..")] },
    proxy: {
      "/api": {
        target: "http://localhost:8082",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
  },
});
