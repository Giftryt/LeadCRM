import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// In development the Vite server proxies /api to the Spring Boot API, so the app and API share an origin
// (cookies just work, no CORS needed). In production nginx does the same (see apps/web/nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": { target: process.env.API_URL ?? "http://localhost:4000", changeOrigin: true } },
  },
});
