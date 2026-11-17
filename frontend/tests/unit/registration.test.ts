import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createApp, nextTick, type App } from "vue";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import AuthView from "../../src/views/AuthView.vue";
import { api } from "../../src/api";
import { ApiError } from "../../src/api/client";
let app: App, root: HTMLDivElement;
const tick = async () => {
  await new Promise((r) => setTimeout(r, 0));
  await nextTick();
};
beforeEach(() => {
  root = document.createElement("div");
  document.body.append(root);
});
afterEach(() => {
  app?.unmount();
  root.remove();
  vi.restoreAllMocks();
});
async function mount() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/register", component: AuthView },
      { path: "/login", component: { template: "<p>登录页</p>" } },
    ],
  });
  await router.push("/register");
  app = createApp(AuthView);
  app.use(createPinia()).use(router).mount(root);
  await tick();
  return router;
}
async function input(selector: string, value: string) {
  const el = root.querySelector<HTMLInputElement>(selector)!;
  el.value = value;
  el.dispatchEvent(new Event("input"));
  await tick();
}
async function click(text: string) {
  const el = [...root.querySelectorAll("button")].find(
    (b) => b.textContent?.trim() === text,
  )!;
  el.click();
  await tick();
}
async function submit() {
  root
    .querySelector("form")!
    .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  await tick();
}
it("发送、验证、携带票据注册后进入登录页", async () => {
  vi.spyOn(api, "sendRegistrationCode").mockResolvedValue({ expiresIn: 300 });
  vi.spyOn(api, "verifyRegistrationCode").mockResolvedValue({
    signupTicket: "ticket",
    expiresIn: 300,
  });
  const register = vi.spyOn(api, "register").mockResolvedValue({
    id: "123",
    email: "reader@example.com",
    roles: ["USER"],
  });
  const router = await mount();
  await input("input[type=email]", "reader@example.com");
  await click("发送验证码");
  expect(root.textContent).toContain("验证码已发送");
  await input('input[aria-label="验证码"]', "012345");
  await submit();
  expect(root.textContent).toContain("第二步：设置密码");
  await input('input[aria-label="密码"]', "password123");
  await input('input[aria-label="确认密码"]', "password123");
  await submit();
  expect(register).toHaveBeenCalledWith(
    "reader@example.com",
    "password123",
    "ticket",
  );
  expect(router.currentRoute.value.path).toBe("/login");
});
it.each([
  [3003, "验证码错误"],
  [3004, "验证码已过期，请重新获取"],
])("显示注册验证错误 %s", async (code, msg) => {
  vi.spyOn(api, "verifyRegistrationCode").mockRejectedValue(
    new ApiError(msg, 400, code),
  );
  await mount();
  await input("input[type=email]", "reader@example.com");
  await input('input[aria-label="验证码"]', "123456");
  await submit();
  expect(root.textContent).toContain(msg);
  expect(root.textContent).toContain("第一步：验证邮箱");
});
it("票据无效时回到邮箱验证步骤", async () => {
  vi.spyOn(api, "verifyRegistrationCode").mockResolvedValue({
    signupTicket: "ticket",
    expiresIn: 300,
  });
  vi.spyOn(api, "register").mockRejectedValue(
    new ApiError("注册凭证无效，请重新验证邮箱", 400, 3005),
  );
  await mount();
  await input("input[type=email]", "reader@example.com");
  await input('input[aria-label="验证码"]', "123456");
  await submit();
  await input('input[aria-label="密码"]', "password123");
  await input('input[aria-label="确认密码"]', "password123");
  await submit();
  expect(root.textContent).toContain("第一步：验证邮箱");
  expect(root.textContent).toContain("注册凭证无效");
});

it("重复邮箱不能发送注册验证码", async () => {
  vi.spyOn(api, "sendRegistrationCode").mockRejectedValue(
    new ApiError("邮箱已注册", 409, 3001),
  );
  await mount();
  await input("input[type=email]", "reader@example.com");
  await click("发送验证码");
  expect(root.textContent).toContain("邮箱已注册");
  expect(root.textContent).toContain("第一步：验证邮箱");
});
