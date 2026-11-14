import { beforeEach, expect, it, vi } from "vitest";
import type { InternalAxiosRequestConfig } from "axios";
import { http, request } from "../../src/api/client";
import { readSession, writeSession } from "../../src/lib/session";
const login = {
  accessToken: "old-token",
  tokenType: "Bearer",
  expiresIn: 7200,
  user: { id: "12", email: "a@example.com", roles: ["USER"] as ["USER"] },
};
beforeEach(() => {
  sessionStorage.clear();
  writeSession(login);
});
it("旧请求401不清除后来新登录的会话", async () => {
  let reject!: (value: unknown) => void;
  let sent: InternalAxiosRequestConfig | undefined;
  http.defaults.adapter = (config) => {
    sent = config;
    return new Promise((_, r) => {
      reject = r;
    });
  };
  const response = request("/hospitals").catch((error) => error);
  await vi.waitFor(() => expect(sent).toBeDefined());
  writeSession({ ...login, accessToken: "new-token" });
  reject({
    config: sent,
    response: { status: 401, data: { code: 2001, message: "登录过期" } },
  });
  await response;
  expect(readSession()?.token).toBe("new-token");
});
it("当前会话401清除身份", async () => {
  http.defaults.adapter = (config) =>
    Promise.reject({
      config,
      response: { status: 401, data: { code: 2001, message: "登录过期" } },
    });
  await expect(request("/hospitals")).rejects.toThrow("登录过期");
  expect(readSession()).toBeNull();
});
