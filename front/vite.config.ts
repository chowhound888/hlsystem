import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          "element-plus": ["element-plus"],
          "vue-vendor": ["vue", "vue-router", "pinia"],
          charts: [
            "echarts/core",
            "echarts/charts",
            "echarts/components",
            "echarts/renderers",
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    host: "0.0.0.0",
    allowedHosts: true,
    hmr: {
      host: "localhost",
    },
    proxy: { "/api": { target: "http://127.0.0.1:8080", changeOrigin: true } },
  },
});
