import { defineConfig, loadEnv, type Plugin } from "vite";
import vue from "@vitejs/plugin-vue";

const profiles = new Map([
  ["development", "dev"],
  ["test", "test"],
  ["demo", "demo"],
]);
const host = "127.0.0.1";
const port = 5173;
const target = "http://127.0.0.1:8080";

function localEnvironment(
  profile: string,
  launchId: string | undefined,
): Plugin {
  async function checkBackend() {
    const response = await fetch(target + "/api/v1/local-environment", {
      signal: AbortSignal.timeout(3000),
    }).catch(() => null);
    if (!response?.ok) throw new Error("请先启动已就绪的后端（端口8080）");
    const status = await response.json();
    if (
      status.profile !== profile ||
      status.ready !== true ||
      (launchId && status.launchId !== launchId)
    ) {
      throw new Error("前端与后端环境或启动实例不匹配，请先停止当前环境");
    }
  }

  return {
    name: "appoint-local-environment",
    async configureServer(server) {
      const options = server.config.server;
      if (
        options.host !== host ||
        options.port !== port ||
        !options.strictPort
      ) {
        throw new Error("前端固定监听127.0.0.1:5173，请移除监听配置覆盖");
      }
      await checkBackend();
      let ready = false;
      server.middlewares.use((request, response, next) => {
        if (request.url === "/__local/ready" && request.method === "GET") {
          response.statusCode = ready ? 200 : 503;
          response.setHeader("Content-Type", "application/json");
          response.setHeader("Cache-Control", "no-store");
          response.end(JSON.stringify({ profile, ready, launchId }));
        } else if (ready) {
          next();
        } else {
          response.statusCode = 503;
          response.end("Frontend is waiting for the matching backend");
        }
      });

      server.httpServer?.once("listening", async () => {
        try {
          await checkBackend();
          ready = true;
        } catch (error) {
          server.config.logger.error(String(error));
          await server.close();
          process.exitCode = 1;
        }
      });
    },
  };
}

export default defineConfig(({ mode, command, isPreview }) => {
  const env = { ...loadEnv(mode, process.cwd(), ""), ...process.env };
  const profile = profiles.get(mode);
  const serve = command === "serve" && !isPreview;
  if (serve) {
    if (!profile) throw new Error("只支持development/test/demo常驻模式");
    if (env.API_PROXY_TARGET && env.API_PROXY_TARGET !== target)
      throw new Error("API_PROXY_TARGET必须为" + target);
  }
  return {
    plugins: [
      vue(),
      serve && profile
        ? localEnvironment(profile, process.env.APPOINT_LOCAL_LAUNCH_ID)
        : undefined,
    ],
    define: {
      "import.meta.env.VITE_APPOINT_ENVIRONMENT": JSON.stringify(profile ?? ""),
      "import.meta.env.VITE_ENABLE_MOCK_PAY": JSON.stringify(Boolean(profile)),
    },
    server: {
      host,
      port,
      strictPort: true,
      proxy: { "/api": { target } },
    },
  };
});
