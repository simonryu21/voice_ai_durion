import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: true,
    proxy: {
      "/api": {
        target: "http://0.0.0.0:7860",
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (id.includes("@daily-co")) return "vendor-daily";
          if (id.includes("@pipecat-ai")) return "vendor-pipecat";
          if (id.includes("@radix-ui")) return "vendor-radix";
          if (id.includes("chart.js") || id.includes("react-chartjs-2"))
            return "vendor-charts";
          if (id.includes("/three/") || id.includes("@types/three"))
            return "vendor-three";
          if (id.includes("lucide-react")) return "vendor-icons";
          if (id.includes("react-markdown") || id.includes("/remark-"))
            return "vendor-markdown";
          if (id.includes("zustand") || id.includes("jotai"))
            return "vendor-state";
          if (
            id.includes("/react/") ||
            id.includes("/react-dom/") ||
            id.includes("scheduler")
          ) {
            return "vendor-react";
          }
          return "vendor";
        },
      },
    },
  },
});
