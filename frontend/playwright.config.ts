import { defineConfig } from "@playwright/test";
const frontendPort = process.env.COMPOSE_REAL_TEST === "1" ? 5174 : 5175;
export default defineConfig({
  testDir: "./tests/e2e",
  testMatch:
    process.env.COMPOSE_REAL_TEST === "1"
      ? "**/compose-real.spec.ts"
      : process.env.REAL_API_TEST === "1"
        ? "**/real.spec.ts"
        : "**/flows.spec.ts",
  timeout: 30000,
  workers: 1,
  retries: 0,
  reporter: [
    ["list"],
    [
      "json",
      {
        outputFile: process.env.E2E_REPORT_FILE || "test-results/results.json",
      },
    ],
  ],
  use: {
    baseURL: `http://127.0.0.1:${frontendPort}`,
    viewport: { width: 1280, height: 900 },
    launchOptions: {
      executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
      args: ["--no-sandbox", "--disable-dev-shm-usage"],
    },
    screenshot: "only-on-failure",
    trace: process.env.COMPOSE_REAL_TEST === "1" ? "off" : "retain-on-failure",
  },
  webServer: {
    command: `npm run dev -- --port ${frontendPort}`,
    url: `http://127.0.0.1:${frontendPort}`,
    reuseExistingServer: false,
    env: {
      VITE_ENABLE_MOCK_PAY: "true",
      API_PROXY_TARGET: process.env.REAL_API_TARGET || "http://127.0.0.1:8080",
    },
  },
});
