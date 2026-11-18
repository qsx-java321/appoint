import { afterEach, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllEnvs();
  sessionStorage.clear();
});

it("同一个地址的会话及预约草稿按环境隔离，原页面请求仍发送原环境", async () => {
  vi.stubEnv("VITE_APPOINT_ENVIRONMENT", "demo");
  vi.resetModules();
  const demo = await import("../../src/lib/session");
  const demoApi = await import("../../src/api/client");
  const login = {
    accessToken: "demo-token",
    tokenType: "Bearer",
    expiresIn: 60,
    user: { id: "1", email: "demo@example.com", roles: ["USER"] as const },
  };
  demo.writeSession({ ...login, user: { ...login.user, roles: ["USER"] } });
  sessionStorage.setItem("appoint.demo.submission.v1", "demo-appointment");

  vi.stubEnv("VITE_APPOINT_ENVIRONMENT", "test");
  vi.resetModules();
  const test = await import("../../src/lib/session");
  expect(test.readSession()).toBeNull();
  test.clearSession();
  expect(demo.readSession()?.token).toBe("demo-token");
  expect(sessionStorage.getItem("appoint.demo.submission.v1")).toBe(
    "demo-appointment",
  );

  demoApi.http.defaults.adapter = async (config) => {
    expect(config.headers.get("X-Appoint-Environment")).toBe("demo");
    return { config, data: {}, status: 200, statusText: "OK", headers: {} };
  };
  await demoApi.http.get("/users/me");
});
