import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

const mappings: Record<string, [number, number]> = {
  development: [5173, 8080],
  test: [5174, 8081],
  demo: [5175, 8082],
};

export default defineConfig(({ mode, command }) => {
  const env = { ...loadEnv(mode, process.cwd(), ""), ...process.env };
  const mapping = mappings[mode];
  if (command === "serve" && !mapping)
    throw new Error("只支持development/test/demo常驻模式");
  const [port, backend] = mapping ?? [5173, 8080];
  const target = `http://127.0.0.1:${backend}`;
  if (command === "serve") {
    if (env.API_PROXY_TARGET && env.API_PROXY_TARGET !== target)
      throw new Error("API_PROXY_TARGET与所选环境不符");
    const portOption = process.argv.findIndex(
      (value) => value === "--port" || value.startsWith("--port="),
    );
    if (portOption >= 0) {
      const value = process.argv[portOption]!;
      const requested = Number(
        value.includes("=") ? value.split("=")[1] : process.argv[portOption + 1],
      );
      if (requested !== port) throw new Error("命令行端口与环境映射不符");
    }
  }
  return {
    plugins: [vue()],
    define: {
      "import.meta.env.VITE_ENABLE_MOCK_PAY": JSON.stringify(mapping ? "true" : "false"),
    },
    server: {
      host: "127.0.0.1",
      port,
      strictPort: true,
      proxy: { "/api": { target } },
    },
  };
});
