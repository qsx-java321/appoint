import { test, expect } from "@playwright/test";

test("真实隔离API：注册登录、目录、就诊人、创建与未支付取消", async ({
  page,
}, info) => {
  const requests: { method: string; path: string; status: number }[] = [];
  page.on("response", (response) => {
    const url = new URL(response.url());
    if (url.pathname.startsWith("/api/"))
      requests.push({
        method: response.request().method(),
        path: url.pathname,
        status: response.status(),
      });
  });
  page.on("dialog", (dialog) => dialog.accept());
  const email = `frontend-${Date.now()}@example.com`;
  await page.goto("/register");
  await page.getByLabel("邮箱", { exact: true }).fill(email);
  await page.getByLabel("密码", { exact: true }).fill("Frontend-test-2026");
  await page.getByRole("button", { name: "注册账号", exact: true }).click();
  await expect(page.getByRole("heading", { name: "欢迎登录" })).toBeVisible();
  await page.getByLabel("密码", { exact: true }).fill("Frontend-test-2026");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.getByRole("link", { name: "选择医院" }).first().click();
  await page.getByRole("link", { name: "查看场次" }).first().click();
  await expect(page.locator(".slot")).toHaveCount(12);
  await page.locator(".day").nth(2).getByRole("button").nth(2).click();
  await page.getByRole("button", { name: "下一步：核对预约" }).click();
  await page.getByRole("link", { name: "新增就诊人" }).click();
  await page.getByLabel("姓名", { exact: true }).fill("接口验收就诊人");
  await page
    .getByLabel("身份证号码", { exact: true })
    .fill("11010519491231002X");
  await page.getByLabel("联系电话").fill("13800138000");
  await page.getByLabel("联系邮箱").fill("patient@example.com");
  await page.getByRole("button", { name: "保存就诊人" }).click();
  await expect(
    page.getByRole("heading", { name: "核对这次预约" }),
  ).toBeVisible();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "提交预约，前往支付" }).click();
  await expect(page).toHaveURL(/\/appointments\/\d+\/payment/);
  await expect(
    page.getByRole("heading", { name: "待支付", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "取消预约" }).click();
  await expect(
    page.getByRole("heading", { name: "预约已取消", exact: true }),
  ).toBeVisible();
  expect(
    requests.some(
      (r) =>
        r.method === "POST" &&
        r.path === "/api/v1/appointments" &&
        r.status === 201,
    ),
  ).toBe(true);
  expect(requests.filter((r) => r.status >= 400)).toEqual([]);
  await info.attach("real-http-summary", {
    body: JSON.stringify(requests, null, 2),
    contentType: "application/json",
  });
});
