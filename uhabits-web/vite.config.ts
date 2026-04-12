import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { playwright } from "@vitest/browser-playwright";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // uhabits-core (file: link) imports these as bare specifiers.
      // Vite can't resolve them from inside the linked package, so we
      // point them at the web project's own node_modules.
      "sprintf-js": path.resolve(__dirname, "node_modules/sprintf-js"),
      jszip: path.resolve(__dirname, "node_modules/jszip"),
    },
  },
  build: {
    target: "esnext",
  },
  test: {
    browser: {
      enabled: true,
      provider: playwright(),
      instances: [{ browser: "chromium" }],
    },
  },
});
