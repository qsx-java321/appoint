import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { safeReturn, writeSession, readSession } from "../../src/lib/session";
import { api } from "../../src/api";
import { useAuth } from "../../src/stores/auth";
import { epoch } from "../../src/lib/presentation";
const login = {
  accessToken: "token-a",
  expiresIn: 7200,
  tokenType: "Bearer",
  user: {
    id: "9007199254740999",
    email: "demo@example.com",
    roles: ["USER"] as const,
  },
};
beforeEach(() => {
  sessionStorage.clear();
  setActivePinia(createPinia());
});
describe("身份和安全返回地址", () => {
  it.each([
    "https://evil.test",
    "//evil.test",
    "/\\evil.test",
    "/login",
    "/booking\n//evil.test",
    "/bookings",
  ])("拒绝无效返回路径 %s", (path) => {
    expect(safeReturn(path)).toBe("/booking");
  });
  it("保留本站业务路径", () => {
    expect(safeReturn("/appointments/9007199254740999/payment")).toBe(
      "/appointments/9007199254740999/payment",
    );
  });
  it("保留消息中心返回路径", () => {
    expect(safeReturn("/notifications")).toBe("/notifications");
  });
  it("过期会话不可恢复，ID不转数字", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-14T00:00:00Z"));
    writeSession({ ...login, user: { ...login.user, roles: ["USER"] } });
    expect(readSession()?.user.id).toBe("9007199254740999");
    vi.advanceTimersByTime(7200000);
    expect(readSession()).toBeNull();
    vi.useRealTimers();
  });
  it("资料中的新增角色不会扩大旧JWT权限", async () => {
    writeSession({ ...login, user: { ...login.user, roles: ["USER"] } });
    vi.spyOn(api, "me").mockResolvedValue({
      ...login.user,
      roles: ["USER", "ADMIN"],
    });
    const auth = useAuth();
    await auth.restore();
    expect(auth.profile?.roles).toContain("ADMIN");
    expect(auth.hasRole("ADMIN")).toBe(false);
  });
  it("登录错误不留下会话", async () => {
    vi.spyOn(api, "login").mockRejectedValue(new Error("账号或密码错误"));
    await expect(useAuth().login("demo@example.com", "wrong")).rejects.toThrow(
      "账号或密码错误",
    );
    expect(readSession()).toBeNull();
  });
});
it("上海LocalDateTime不受浏览器时区影响", () => {
  expect(epoch("2026-11-14T08:00:00")).toBe(Date.parse("2026-11-14T00:00:00Z"));
});
