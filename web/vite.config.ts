import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// detail.md §13: web served on :3000, proxied to the control plane on :8080
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      "/api": "http://localhost:8080",
      "/agent": "http://localhost:8080",
    },
  },
});
