import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import basicSsl from "@vitejs/plugin-basic-ssl";

export default defineConfig({
  base: "./", //Use relative paths so it works at any mount path
  plugins: [react(), basicSsl()],
  publicDir: "public",
  server: {
    https: true, // Enable HTTPS for mobile access
    host: "0.0.0.0", // Allow access from network devices (phones, etc.)
    port: 5174, // OAuth redirect URI(*_REDIRECT_URI)와 일치시키기 위해 고정
    strictPort: true, // 5174 점유 시 자동 이동 막아 OAuth 등록 URL 깨지지 않게
    allowedHosts: true, // Allows external connections like ngrok
    proxy: {
      // Proxy /api requests to the backend server
      "/api": {
        target: "https://localhost:7860", // Replace with your backend URL
        changeOrigin: true,
        secure: false, // Allow self-signed certificates from backend
        ws: true, // Enable WebSocket proxying for WebRTC signaling
      },
      "/start": {
        target: "http://localhost:7860", // Replace with your backend URL
        changeOrigin: true,
      },
      "/sessions": {
        target: "http://localhost:7860", // Replace with your backend URL
        changeOrigin: true,
      },
    },
  },
});
