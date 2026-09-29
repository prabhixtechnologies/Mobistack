import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, URL } from "node:url";

/**
 * Directories Vite may read outside this project.
 *
 * `@prabhixtechnologies/brand` is a `file:` dependency on a sibling checkout, so node_modules/@prabhixtechnologies/brand
 * is a link that leaves this repository, and Vite resolves links to their real path before
 * checking `server.fs.allow`. Its entry point builds mark URLs with
 * `new URL("../marks/...", import.meta.url)`, which Vite rewrites into asset imports resolving
 * inside web-kit, so importing the package without this fails with "Denied ID" rather than a
 * missing file. Three files here import it — the compatibility page, the workspace list and
 * brandTone — and the failure is waiting for the first test that touches one.
 *
 * Absent before `npm install`, in which case there is nothing to allow.
 */
const linkedPackages = ["@prabhixtechnologies/brand"]
  .map((name) => path.resolve(import.meta.dirname, "node_modules", name))
  .filter((dir) => fs.existsSync(dir))
  .map((dir) => fs.realpathSync(dir));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
    dedupe: ["react", "react-dom"],
  },
  optimizeDeps: {
    include: ["@prabhixtechnologies/oidc-client"],
  },
  server: {
    // Local Identity redirect is :5176; compose publishes API on :8082.
    port: 5176,
    fs: { allow: [path.resolve(import.meta.dirname, ".."), ...linkedPackages] },
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
